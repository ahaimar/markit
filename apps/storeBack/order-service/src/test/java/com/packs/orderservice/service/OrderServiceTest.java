package com.packs.orderservice.service;

import com.packs.orderservice.client.ProductClient;
import com.packs.orderservice.dto.OrderDto;
import com.packs.orderservice.dto.PlaceOrderRequest;
import com.packs.orderservice.entity.Cart;
import com.packs.orderservice.entity.CartItem;
import com.packs.orderservice.entity.IdempotencyRecord;
import com.packs.orderservice.entity.Order;
import com.packs.orderservice.entity.OrderItem;
import com.packs.orderservice.entity.OutboxEvent;
import com.packs.orderservice.repository.CartItemRepository;
import com.packs.orderservice.repository.CartRepository;
import com.packs.orderservice.repository.IdempotencyRecordRepository;
import com.packs.orderservice.repository.OrderItemRepository;
import com.packs.orderservice.repository.OrderRepository;
import com.packs.orderservice.repository.OutboxEventRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.OrderStatus;
import com.packs.sharedlib.ProductDto;
import com.packs.sharedlib.StockDecrementRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private CartRepository cartRepository;
	@Mock
	private CartItemRepository cartItemRepository;
	@Mock
	private OrderRepository orderRepository;
	@Mock
	private OrderItemRepository orderItemRepository;
	@Mock
	private OutboxEventRepository outboxEventRepository;
	@Mock
	private IdempotencyRecordRepository idempotencyRecordRepository;
	@Mock
	private ProductClient productClient;

	private OrderService orderService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private UUID userId;
	private UUID cartId;
	private UUID productId;
	private UUID orderId;

	@BeforeEach
	void setUp() {
		orderService = new OrderService(
			cartRepository, cartItemRepository, orderRepository, orderItemRepository,
			outboxEventRepository, idempotencyRecordRepository, productClient, objectMapper);
		userId = UUID.randomUUID();
		cartId = UUID.randomUUID();
		productId = UUID.randomUUID();
		orderId = UUID.randomUUID();
	}

	private Cart cart(UUID owner) {
		Cart cart = new Cart();
		cart.setId(cartId);
		cart.setUserId(owner);
		cart.setCreatedAt(Instant.now());
		cart.setUpdatedAt(Instant.now());
		return cart;
	}

	private CartItem cartItem(int quantity) {
		CartItem item = new CartItem();
		item.setId(UUID.randomUUID());
		item.setCartId(cartId);
		item.setProductId(productId);
		item.setQuantity(quantity);
		item.setCreatedAt(Instant.now());
		return item;
	}

	private void mockOrderSave() {
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
			Order order = invocation.getArgument(0);
			order.setId(orderId);
			return order;
		});
	}

	@Test
	void placeOrder_persistsOrderAndOutboxAndClearsCart() throws Exception {
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(userId)));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem(2)));
		when(productClient.getProduct(productId.toString()))
			.thenReturn(new ProductDto(productId.toString(), "Ceramic Mug", "Stoneware", new BigDecimal("18.50"), "Mugs", 50));
		doNothing().when(productClient).decrementStock(anyString(), any(StockDecrementRequest.class));
		mockOrderSave();

		OrderDto result = orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null);

		assertEquals("PENDING", result.status());
		assertEquals(0, new BigDecimal("37.00").compareTo(result.totalPrice()));
		assertEquals(userId.toString(), result.userId());

		verify(orderRepository).save(any(Order.class));
		verify(orderItemRepository).save(any(OrderItem.class));
		verify(outboxEventRepository).save(any(OutboxEvent.class));
		verify(cartItemRepository).deleteByCartId(cartId);
	}

	@Test
	void placeOrder_emptyCart_throwsBadRequest() {
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(userId)));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

		ApiException ex = assertThrows(ApiException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertEquals("ERR_CART_EMPTY", ex.getCode());
	}

	@Test
	void placeOrder_cartOwnedByAnotherUser_throwsForbidden() {
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(UUID.randomUUID())));

		ApiException ex = assertThrows(ApiException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null));

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
		assertEquals("ERR_UNAUTHORIZED", ex.getCode());
	}

	@Test
	void placeOrder_cartNotFound_throwsNotFound() {
		when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		assertEquals("ERR_CART_NOT_FOUND", ex.getCode());
	}

	@Test
	void placeOrder_duplicateIdempotencyKey_returnsCachedResponse() throws Exception {
		OrderDto expected = buildOrderDto();
		IdempotencyRecord record = new IdempotencyRecord();
		record.setUserId(userId);
		record.setIdempotencyKey("key-1");
		record.setRequestHash(requestHash(new PlaceOrderRequest(cartId, "1 Main St")));
		record.setResponsePayload(objectMapper.writeValueAsString(expected));
		when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, "key-1")).thenReturn(Optional.of(record));

		OrderDto result = orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, "key-1");

		assertEquals(expected, result);
		verifyNoInteractions(cartRepository);
	}

	@Test
	void placeOrder_expiredIdempotencyRecord_isDiscardedAndReprocessed() throws Exception {
		IdempotencyRecord record = new IdempotencyRecord();
		record.setUserId(userId);
		record.setIdempotencyKey("key-1");
		record.setRequestHash(requestHash(new PlaceOrderRequest(cartId, "1 Main St")));
		record.setResponsePayload("stale");
		record.setExpiresAt(Instant.now().minusSeconds(60));
		when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, "key-1")).thenReturn(Optional.of(record));
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(userId)));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem(1)));
		when(productClient.getProduct(productId.toString()))
			.thenReturn(new ProductDto(productId.toString(), "Ceramic Mug", "Stoneware", new BigDecimal("18.50"), "Mugs", 50));
		mockOrderSave();

		OrderDto result = orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, "key-1");

		assertEquals("PENDING", result.status());
		verify(idempotencyRecordRepository).delete(record);
		verify(orderRepository).save(any(Order.class));
		verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
	}

	@Test
	void placeOrder_idempotencyMismatch_throwsUnprocessable() {
		IdempotencyRecord record = new IdempotencyRecord();
		record.setUserId(userId);
		record.setIdempotencyKey("key-1");
		record.setRequestHash("different-hash");
		when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, "key-1")).thenReturn(Optional.of(record));

		ApiException ex = assertThrows(ApiException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "different-address"), userId, "key-1"));

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
		assertEquals("ERR_IDEMPOTENCY_MISMATCH", ex.getCode());
	}

	@Test
	void placeOrder_productOutOfStock_compensatesPriorDecrementsAndFails() {
		UUID firstProduct = UUID.randomUUID();
		UUID secondProduct = UUID.randomUUID();
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(userId)));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item(firstProduct, 1), item(secondProduct, 1)));
		when(productClient.getProduct(firstProduct.toString()))
			.thenReturn(new ProductDto(firstProduct.toString(), "A", "d", new BigDecimal("1.00"), "c", 5));
		doNothing().when(productClient).decrementStock(firstProduct.toString(), new StockDecrementRequest(1));
		doThrow(new ApiException("ERR_INSUFFICIENT_STOCK", "Insufficient stock", HttpStatus.CONFLICT))
			.when(productClient).decrementStock(secondProduct.toString(), new StockDecrementRequest(1));

		ApiException ex = assertThrows(ApiException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
		verify(productClient).incrementStock(firstProduct.toString(), new StockDecrementRequest(1));
		verify(productClient, never()).incrementStock(eq(secondProduct.toString()), any(StockDecrementRequest.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void placeOrder_productServiceDown_compensatesPriorDecrementsAndFails() {
		UUID firstProduct = UUID.randomUUID();
		UUID secondProduct = UUID.randomUUID();
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(userId)));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item(firstProduct, 1), item(secondProduct, 1)));
		when(productClient.getProduct(firstProduct.toString()))
			.thenReturn(new ProductDto(firstProduct.toString(), "A", "d", new BigDecimal("1.00"), "c", 5));
		doNothing().when(productClient).decrementStock(firstProduct.toString(), new StockDecrementRequest(1));
		when(productClient.getProduct(secondProduct.toString()))
			.thenThrow(new ApiException("ERR_PRODUCT_SERVICE_UNAVAILABLE", "down", HttpStatus.SERVICE_UNAVAILABLE));

		ApiException ex = assertThrows(ApiException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
		verify(productClient).incrementStock(firstProduct.toString(), new StockDecrementRequest(1));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void placeOrder_localPersistenceFails_compensatesAllDecrementedStock() {
		UUID firstProduct = UUID.randomUUID();
		UUID secondProduct = UUID.randomUUID();
		when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(userId)));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item(firstProduct, 1), item(secondProduct, 1)));
		when(productClient.getProduct(firstProduct.toString()))
			.thenReturn(new ProductDto(firstProduct.toString(), "A", "d", new BigDecimal("1.00"), "c", 5));
		doNothing().when(productClient).decrementStock(firstProduct.toString(), new StockDecrementRequest(1));
		when(productClient.getProduct(secondProduct.toString()))
			.thenReturn(new ProductDto(secondProduct.toString(), "B", "d", new BigDecimal("2.00"), "c", 5));
		doNothing().when(productClient).decrementStock(secondProduct.toString(), new StockDecrementRequest(1));
		when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("db down"));

		RuntimeException ex = assertThrows(RuntimeException.class,
			() -> orderService.placeOrder(new PlaceOrderRequest(cartId, "1 Main St"), userId, null));

		assertEquals("db down", ex.getMessage());
		verify(productClient).incrementStock(firstProduct.toString(), new StockDecrementRequest(1));
		verify(productClient).incrementStock(secondProduct.toString(), new StockDecrementRequest(1));
		verify(cartItemRepository, never()).deleteByCartId(cartId);
	}

	@Test
	void updateStatus_validTransition_persistsStatusChangedOutbox() throws Exception {
		Order order = orderInState(OrderStatus.PENDING, userId);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
		when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

		OrderDto result = orderService.updateStatus(orderId, "CONFIRMED");

		assertEquals("CONFIRMED", result.status());
		verify(orderRepository).save(order);
		verify(outboxEventRepository).save(any(OutboxEvent.class));
	}

	@Test
	void updateStatus_toCancelled_persistsCancelledOutbox() throws Exception {
		Order order = orderInState(OrderStatus.PENDING, userId);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
		when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

		OrderDto result = orderService.updateStatus(orderId, "CANCELLED");

		assertEquals("CANCELLED", result.status());
		verify(orderRepository).save(order);
		verify(outboxEventRepository).save(any(OutboxEvent.class));
	}

	@Test
	void updateStatus_cancel_restoresStockForEachItem() {
		Order order = orderInState(OrderStatus.CONFIRMED, userId);
		UUID productA = UUID.randomUUID();
		UUID productB = UUID.randomUUID();
		com.packs.orderservice.entity.OrderItem a = new com.packs.orderservice.entity.OrderItem();
		a.setProductId(productA);
		a.setQuantity(2);
		com.packs.orderservice.entity.OrderItem b = new com.packs.orderservice.entity.OrderItem();
		b.setProductId(productB);
		b.setQuantity(1);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
		when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(a, b));

		OrderDto result = orderService.updateStatus(orderId, "CANCELLED");

		assertEquals("CANCELLED", result.status());
		verify(productClient).incrementStock(productA.toString(), new StockDecrementRequest(2));
		verify(productClient).incrementStock(productB.toString(), new StockDecrementRequest(1));
		verify(orderRepository).save(order);
		verify(outboxEventRepository).save(any(OutboxEvent.class));
	}

	@Test
	void updateStatus_cancel_stockRestoreFailureStillCancels() {
		Order order = orderInState(OrderStatus.CONFIRMED, userId);
		UUID productA = UUID.randomUUID();
		com.packs.orderservice.entity.OrderItem a = new com.packs.orderservice.entity.OrderItem();
		a.setProductId(productA);
		a.setQuantity(3);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
		when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(a));
		doThrow(new ApiException("ERR_PRODUCT_SERVICE_UNAVAILABLE", "down", HttpStatus.SERVICE_UNAVAILABLE))
			.when(productClient).incrementStock(anyString(), any(StockDecrementRequest.class));

		OrderDto result = orderService.updateStatus(orderId, "CANCELLED");

		assertEquals("CANCELLED", result.status());
		verify(orderRepository).save(order);
		verify(outboxEventRepository).save(any(OutboxEvent.class));
	}

	@Test
	void updateStatus_invalidTransition_throwsConflict() {
		Order order = orderInState(OrderStatus.DELIVERED, userId);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

		ApiException ex = assertThrows(ApiException.class, () -> orderService.updateStatus(orderId, "SHIPPED"));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
		assertEquals("ERR_INVALID_TRANSITION", ex.getCode());
		verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
	}

	@Test
	void updateStatus_invalidStatusValue_throwsBadRequest() {
		Order order = orderInState(OrderStatus.PENDING, userId);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

		ApiException ex = assertThrows(ApiException.class, () -> orderService.updateStatus(orderId, "NONSENSE"));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertEquals("ERR_INVALID_STATUS", ex.getCode());
	}

	@Test
	void getOrder_notFound_throwsNotFound() {
		when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> orderService.getOrder(orderId));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		assertEquals("ERR_ORDER_NOT_FOUND", ex.getCode());
	}

	@Test
	void listOrders_returnsPageForUser() {
		Order order = orderInState(OrderStatus.PENDING, userId);
		org.springframework.data.domain.Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
		when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(page);
		when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

		var result = orderService.listOrders(userId, 0, 20);

		assertEquals(1, result.items().size());
		assertEquals(1, result.total());
		assertEquals(orderId.toString(), result.items().get(0).orderId());
	}

	private CartItem item(UUID productId, int quantity) {
		CartItem item = new CartItem();
		item.setId(UUID.randomUUID());
		item.setCartId(cartId);
		item.setProductId(productId);
		item.setQuantity(quantity);
		item.setCreatedAt(Instant.now());
		return item;
	}

	private Order orderInState(OrderStatus status, UUID owner) {
		Order order = new Order();
		order.setId(orderId);
		order.setUserId(owner);
		order.setStatus(status.name());
		order.setTotalPrice(new BigDecimal("37.00"));
		order.setShippingAddress("1 Main St");
		order.setCreatedAt(Instant.now());
		order.setUpdatedAt(Instant.now());
		return order;
	}

	private OrderDto buildOrderDto() {
		return new OrderDto(
			orderId.toString(),
			userId.toString(),
			"PENDING",
			new BigDecimal("37.00"),
			"1 Main St",
			Instant.now(),
			List.of());
	}

	private String requestHash(PlaceOrderRequest request) {
		String raw = userId + "|" + request.cartId() + "|" + request.shippingAddress();
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}