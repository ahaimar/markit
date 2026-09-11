package com.packs.orderservice.service;

import com.packs.orderservice.client.ProductClient;
import com.packs.orderservice.dto.CartAddRequest;
import com.packs.orderservice.dto.CartDto;
import com.packs.orderservice.entity.Cart;
import com.packs.orderservice.entity.CartItem;
import com.packs.orderservice.repository.CartItemRepository;
import com.packs.orderservice.repository.CartRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

	@Mock
	private CartRepository cartRepository;
	@Mock
	private CartItemRepository cartItemRepository;
	@Mock
	private ProductClient productClient;

	private CartService cartService;

	private UUID userId;
	private UUID productId;
	private UUID cartId;

	@BeforeEach
	void setUp() {
		cartService = new CartService(cartRepository, cartItemRepository, productClient);
		userId = UUID.randomUUID();
		productId = UUID.randomUUID();
		cartId = UUID.randomUUID();
	}

	private Cart cart() {
		Cart cart = new Cart();
		cart.setId(cartId);
		cart.setUserId(userId);
		cart.setCreatedAt(Instant.now());
		cart.setUpdatedAt(Instant.now());
		return cart;
	}

	private CartItem item(int quantity) {
		CartItem item = new CartItem();
		item.setId(UUID.randomUUID());
		item.setCartId(cartId);
		item.setProductId(productId);
		item.setQuantity(quantity);
		item.setCreatedAt(Instant.now());
		return item;
	}

	@Test
	void addItem_createsNewCartAndItem() {
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
		when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
			Cart cart = invocation.getArgument(0);
			cart.setId(cartId);
			return cart;
		});
		when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.empty());
		when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item(2)));
		when(productClient.getProduct(productId.toString()))
			.thenReturn(new ProductDto(productId.toString(), "Mug", "d", new BigDecimal("18.50"), "Mugs", 50));

		CartDto dto = cartService.addItem(new CartAddRequest(productId, 2), userId);

		assertEquals(cartId.toString(), dto.cartId());
		assertEquals(userId.toString(), dto.userId());
		assertEquals(1, dto.items().size());
		assertEquals(2, dto.items().get(0).quantity());
		assertEquals(0, new BigDecimal("37.00").compareTo(dto.totalPrice()));
		verify(cartRepository).save(any(Cart.class));
	}

	@Test
	void addItem_incrementsQuantityOnExistingItem() {
		CartItem existing = item(2);
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart()));
		when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.of(existing));
		when(cartItemRepository.save(any(CartItem.class))).thenReturn(existing);
		when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(existing));
		when(productClient.getProduct(productId.toString()))
			.thenReturn(new ProductDto(productId.toString(), "Mug", "d", new BigDecimal("18.50"), "Mugs", 50));

		CartDto dto = cartService.addItem(new CartAddRequest(productId, 3), userId);

		assertEquals(5, dto.items().get(0).quantity());
		assertEquals(0, new BigDecimal("92.50").compareTo(dto.totalPrice()));
	}

	@Test
	void addItem_productNotFound_throwsBadRequest() {
		when(productClient.getProduct(productId.toString()))
			.thenThrow(new ApiException("ERR_PRODUCT_NOT_FOUND", "not found", HttpStatus.NOT_FOUND));

		ApiException ex = assertThrows(ApiException.class,
			() -> cartService.addItem(new CartAddRequest(productId, 1), userId));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		verify(cartRepository, never()).save(any(Cart.class));
	}

	@Test
	void getCart_noCart_returnsEmptyCart() {
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

		CartDto dto = cartService.getCart(userId);

		assertEquals(userId.toString(), dto.userId());
		assertEquals(0, dto.items().size());
		assertEquals(0, dto.totalPrice().compareTo(BigDecimal.ZERO));
	}

	@Test
	void removeItem_removesItemFromCart() {
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart()));
		when(cartItemRepository.deleteByCartIdAndProductId(cartId, productId)).thenReturn(1);

		cartService.removeItem(productId, userId);

		verify(cartItemRepository).deleteByCartIdAndProductId(cartId, productId);
	}

	@Test
	void removeItem_notInCart_throwsNotFound() {
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart()));
		when(cartItemRepository.deleteByCartIdAndProductId(cartId, productId)).thenReturn(0);

		ApiException ex = assertThrows(ApiException.class, () -> cartService.removeItem(productId, userId));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		assertEquals("ERR_CART_ITEM_NOT_FOUND", ex.getCode());
	}

	@Test
	void clearCart_deletesAllItems() {
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart()));
		doNothing().when(cartItemRepository).deleteByCartId(cartId);

		cartService.clearCart(userId);

		verify(cartItemRepository).deleteByCartId(eq(cartId));
	}

	@Test
	void clearCart_noCart_isNoOp() {
		when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

		cartService.clearCart(userId);

		verify(cartItemRepository, never()).deleteByCartId(any(UUID.class));
	}
}