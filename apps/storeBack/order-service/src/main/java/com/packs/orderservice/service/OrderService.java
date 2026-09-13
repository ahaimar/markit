package com.packs.orderservice.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.packs.orderservice.client.ProductClient;
import com.packs.orderservice.dto.OrderDto;
import com.packs.orderservice.dto.OrderItemDto;
import com.packs.orderservice.dto.PlaceOrderRequest;
import com.packs.orderservice.entity.Order;
import com.packs.orderservice.entity.OrderItem;
import com.packs.orderservice.entity.OutboxEvent;
import com.packs.orderservice.repository.OrderItemRepository;
import com.packs.orderservice.repository.OrderRepository;
import com.packs.orderservice.repository.OutboxEventRepository;
import com.packs.orderservice.entity.CartItem;
import com.packs.orderservice.repository.CartItemRepository;
import com.packs.orderservice.entity.Cart;
import com.packs.orderservice.repository.CartRepository;
import com.packs.orderservice.entity.IdempotencyRecord;
import com.packs.orderservice.repository.IdempotencyRecordRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.OrderCancelledEvent;
import com.packs.sharedlib.OrderCreatedEvent;
import com.packs.sharedlib.OrderStatus;
import com.packs.sharedlib.OrderStatusChangedEvent;
import com.packs.sharedlib.ProductDto;
import com.packs.sharedlib.StockDecrementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final IdempotencyRecordRepository idempotencyRecordRepository;
	private final ProductClient productClient;
	private final ObjectMapper objectMapper;

	@Value("${app.idempotency.ttl-seconds:86400}")
	private long idempotencyTtlSeconds = 86400;

	public OrderService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		OrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		OutboxEventRepository outboxEventRepository,
		IdempotencyRecordRepository idempotencyRecordRepository,
		ProductClient productClient,
		ObjectMapper objectMapper) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.idempotencyRecordRepository = idempotencyRecordRepository;
		this.productClient = productClient;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public OrderDto placeOrder(PlaceOrderRequest request, UUID userId, String idempotencyKey) {
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			String requestHash = hash(request, userId);
			Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
			if (existing.isPresent()) {
				IdempotencyRecord record = existing.get();
				if (isExpired(record)) {
					log.info("Discarding expired idempotency record for key {}", idempotencyKey);
					idempotencyRecordRepository.delete(record);
				} else if (!record.getRequestHash().equals(requestHash)) {
					throw new ApiException("ERR_IDEMPOTENCY_MISMATCH", "The same Idempotency-Key was used with a different request", HttpStatus.UNPROCESSABLE_ENTITY);
				} else {
					try {
						log.info("Returning cached response for idempotency key {}", idempotencyKey);
						return objectMapper.readValue(record.getResponsePayload(), OrderDto.class);
					} catch (JacksonException ex) {
						log.error("Failed to deserialize cached idempotency response", ex);
						throw new ApiException("ERR_IDEMPOTENCY_CACHE", "Failed to read cached response", HttpStatus.INTERNAL_SERVER_ERROR);
					}
				}
			}
		}

		Cart cart = cartRepository.findById(request.cartId())
			.orElseThrow(() -> new ApiException("ERR_CART_NOT_FOUND", "Cart not found", HttpStatus.NOT_FOUND));

		if (!cart.getUserId().equals(userId)) {
			throw new ApiException("ERR_UNAUTHORIZED", "Cart does not belong to the specified user", HttpStatus.FORBIDDEN);
		}

		List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
		if (cartItems.isEmpty()) {
			throw new ApiException("ERR_CART_EMPTY", "Cannot place order with an empty cart", HttpStatus.BAD_REQUEST);
		}

		List<OrderItem> orderItems = new ArrayList<>();
		BigDecimal totalPrice = BigDecimal.ZERO;
		List<Restock> restocks = new ArrayList<>();

		try {
			for (CartItem ci : cartItems) {
				ProductDto product = productClient.getProduct(ci.getProductId().toString());
				productClient.decrementStock(ci.getProductId().toString(), new StockDecrementRequest(ci.getQuantity()));
				restocks.add(new Restock(ci.getProductId(), ci.getQuantity()));

				BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(ci.getQuantity()));
				OrderItem oi = new OrderItem();
				oi.setProductId(ci.getProductId());
				oi.setProductName(product.name());
				oi.setQuantity(ci.getQuantity());
				oi.setPriceAtPurchase(product.price());
				orderItems.add(oi);
				totalPrice = totalPrice.add(lineTotal);
			}
		} catch (RuntimeException ex) {
			compensateStock(restocks);
			restocks.clear();
			throw ex;
		}

		UUID eventId = UUID.randomUUID();

		try {
			Instant now = Instant.now();
			Order order = new Order();
			order.setUserId(userId);
			order.setStatus(OrderStatus.PENDING.name());
			order.setTotalPrice(totalPrice);
			order.setShippingAddress(request.shippingAddress());
			order.setCreatedAt(now);
			order.setUpdatedAt(now);
			Order saved = orderRepository.save(order);

			for (OrderItem oi : orderItems) {
				oi.setOrderId(saved.getId());
				orderItemRepository.save(oi);
			}

			try {
				OrderCreatedEvent event = new OrderCreatedEvent(eventId.toString(), saved.getId().toString(), userId.toString(), totalPrice, now);
				OutboxEvent outbox = new OutboxEvent();
				outbox.setEventType("OrderCreated");
				outbox.setPayload(objectMapper.writeValueAsString(event));
				outbox.setPublished(false);
				outbox.setCreatedAt(now);
				outboxEventRepository.save(outbox);
				log.info("Saved outbox event OrderCreated for order {}", saved.getId());
			} catch (JacksonException ex) {
				log.error("Failed to serialize order event", ex);
			}

			cartItemRepository.deleteByCartId(cart.getId());

			OrderDto result = getOrderDto(saved, orderItems);

			if (idempotencyKey != null && !idempotencyKey.isBlank()) {
				try {
					IdempotencyRecord record = new IdempotencyRecord();
					record.setUserId(userId);
					record.setIdempotencyKey(idempotencyKey);
					record.setRequestHash(hash(request, userId));
					record.setResponsePayload(objectMapper.writeValueAsString(result));
					record.setCreatedAt(Instant.now());
					record.setExpiresAt(record.getCreatedAt().plusSeconds(idempotencyTtlSeconds));
					idempotencyRecordRepository.save(record);
				} catch (JacksonException ex) {
					log.error("Failed to serialize idempotency response", ex);
				}
			}

			return result;
		} catch (RuntimeException ex) {
			compensateStock(restocks);
			throw ex;
		}
	}

	private void compensateStock(List<Restock> restocks) {
		for (Restock restock : restocks) {
			try {
				productClient.incrementStock(restock.productId().toString(), new StockDecrementRequest(restock.quantity()));
				log.warn("Compensated stock: restored {} units of product {}", restock.quantity(), restock.productId());
			} catch (Exception ex) {
				log.error("Failed to restore stock for product {} after order failure", restock.productId(), ex);
			}
		}
	}

	private void restoreStock(List<OrderItem> items) {
		for (OrderItem item : items) {
			try {
				productClient.incrementStock(item.getProductId().toString(), new StockDecrementRequest(item.getQuantity()));
				log.warn("Restored stock for cancelled order: {} units of product {}", item.getQuantity(), item.getProductId());
			} catch (Exception ex) {
				log.error("Failed to restore stock for product {} on cancellation; manual reconciliation required", item.getProductId(), ex);
			}
		}
	}

	private boolean isExpired(IdempotencyRecord record) {
		return record.getExpiresAt() != null && record.getExpiresAt().isBefore(Instant.now());
	}

	private record Restock(UUID productId, int quantity) {
	}

	private String hash(PlaceOrderRequest request, UUID userId) {
		String raw = userId + "|" + request.cartId() + "|" + request.shippingAddress();
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Transactional(readOnly = true)
	public OrderDto getOrder(UUID orderId) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new ApiException("ERR_ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));
		List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
		return getOrderDto(order, items);
	}

	@Transactional(readOnly = true)
	public com.packs.sharedlib.PageResponse<OrderDto> listOrders(UUID userId, int page, int pageSize) {
		org.springframework.data.domain.Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(
			userId,
			org.springframework.data.domain.PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(pageSize, 1), 100)));
		List<OrderDto> dtos = orders.getContent().stream()
			.map(order -> getOrderDto(order, orderItemRepository.findByOrderId(order.getId())))
			.toList();
		return com.packs.sharedlib.PageResponse.of(dtos, orders.getNumber(), orders.getSize(), orders.getTotalElements());
	}

	@Transactional
	public OrderDto updateStatus(UUID orderId, String newStatusValue) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new ApiException("ERR_ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

		OrderStatus currentStatus;
		try {
			currentStatus = OrderStatus.valueOf(order.getStatus());
		} catch (IllegalArgumentException e) {
			throw new ApiException("ERR_INVALID_STATUS", "Current order has invalid status: " + order.getStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
		}

		OrderStatus newStatus;
		try {
			newStatus = OrderStatus.valueOf(newStatusValue);
		} catch (IllegalArgumentException e) {
			throw new ApiException("ERR_INVALID_STATUS", "Invalid status value: " + newStatusValue, HttpStatus.BAD_REQUEST);
		}

		if (!currentStatus.canTransitionTo(newStatus)) {
			throw new ApiException("ERR_INVALID_TRANSITION", "Cannot transition order from " + currentStatus + " to " + newStatus, HttpStatus.CONFLICT);
		}

		List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
		if (newStatus == OrderStatus.CANCELLED) {
			restoreStock(items);
		}

		Instant now = Instant.now();
		String oldStatusStr = order.getStatus();
		order.setStatus(newStatus.name());
		order.setUpdatedAt(now);
		orderRepository.save(order);

		UUID eventId = UUID.randomUUID();

		try {
			if (newStatus == OrderStatus.CANCELLED) {
				OrderCancelledEvent event = new OrderCancelledEvent(eventId.toString(), orderId.toString(), order.getUserId().toString(), order.getTotalPrice(), "Cancelled by admin", now);
				OutboxEvent outbox = new OutboxEvent();
				outbox.setEventType("OrderCancelled");
				outbox.setPayload(objectMapper.writeValueAsString(event));
				outbox.setPublished(false);
				outbox.setCreatedAt(now);
				outboxEventRepository.save(outbox);
				log.info("Saved outbox event OrderCancelled for order {}", orderId);
			} else {
				OrderStatusChangedEvent event = new OrderStatusChangedEvent(eventId.toString(), orderId.toString(), order.getUserId().toString(), order.getTotalPrice(), oldStatusStr, newStatus.name(), now);
				OutboxEvent outbox = new OutboxEvent();
				outbox.setEventType("OrderStatusChanged");
				outbox.setPayload(objectMapper.writeValueAsString(event));
				outbox.setPublished(false);
				outbox.setCreatedAt(now);
				outboxEventRepository.save(outbox);
				log.info("Saved outbox event OrderStatusChanged for order {} from {} to {}", orderId, oldStatusStr, newStatus.name());
			}
		} catch (JacksonException ex) {
			log.error("Failed to serialize order status event", ex);
		}

		return getOrderDto(order, items);
	}

	private OrderDto getOrderDto(Order order, List<OrderItem> items) {
		return new OrderDto(
			order.getId().toString(),
			order.getUserId().toString(),
			order.getStatus(),
			order.getTotalPrice(),
			order.getShippingAddress(),
			order.getCreatedAt(),
			items.stream()
				.map(i -> new OrderItemDto(i.getProductId().toString(), i.getProductName(), i.getQuantity(), i.getPriceAtPurchase()))
				.toList()
		);
	}
}