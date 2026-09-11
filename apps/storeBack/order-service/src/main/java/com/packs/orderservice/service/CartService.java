package com.packs.orderservice.service;

import com.packs.orderservice.client.ProductClient;
import com.packs.orderservice.dto.CartAddRequest;
import com.packs.orderservice.dto.CartDto;
import com.packs.orderservice.dto.CartItemDto;
import com.packs.orderservice.entity.Cart;
import com.packs.orderservice.entity.CartItem;
import com.packs.orderservice.repository.CartItemRepository;
import com.packs.orderservice.repository.CartRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.ProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

	private static final Logger log = LoggerFactory.getLogger(CartService.class);

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductClient productClient;

	public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository, ProductClient productClient) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productClient = productClient;
	}

	@Transactional
	public CartDto addItem(CartAddRequest request, UUID userId) {
		ProductDto product;
		try {
			product = productClient.getProduct(request.productId().toString());
		} catch (ApiException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found: " + request.productId(), HttpStatus.BAD_REQUEST, ex);
		}

		Cart cart = getOrCreateCart(userId);

		CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.productId())
			.map(existing -> {
				existing.setQuantity(existing.getQuantity() + request.quantity());
				return existing;
			})
			.orElseGet(() -> {
				CartItem ci = new CartItem();
				ci.setCartId(cart.getId());
				ci.setProductId(request.productId());
				ci.setQuantity(request.quantity());
				ci.setCreatedAt(Instant.now());
				return ci;
			});

		cartItemRepository.save(item);
		log.info("Added to cart: productId={}, quantity={}", request.productId(), request.quantity());

		return getCartDto(cart);
	}

	@Transactional(readOnly = true)
	public CartDto getCart(UUID userId) {
		Cart cart = cartRepository.findByUserId(userId)
			.orElse(null);
		if (cart == null) {
			return CartDto.empty(userId.toString());
		}
		return getCartDto(cart);
	}

	@Transactional
	public void removeItem(UUID productId, UUID userId) {
		Cart cart = cartRepository.findByUserId(userId)
			.orElseThrow(() -> new ApiException("ERR_CART_NOT_FOUND", "Cart not found", HttpStatus.NOT_FOUND));
		int removed = cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
		if (removed == 0) {
			throw new ApiException("ERR_CART_ITEM_NOT_FOUND", "Item not found in cart: " + productId, HttpStatus.NOT_FOUND);
		}
		log.info("Removed product {} from cart for user {}", productId, userId);
	}

	@Transactional
	public void clearCart(UUID userId) {
		cartRepository.findByUserId(userId).ifPresent(cart -> {
			cartItemRepository.deleteByCartId(cart.getId());
		});
	}

	private Cart getOrCreateCart(UUID userId) {
		return cartRepository.findByUserId(userId).orElseGet(() -> {
			Cart cart = new Cart();
			cart.setUserId(userId);
			cart.setCreatedAt(Instant.now());
			cart.setUpdatedAt(Instant.now());
			return cartRepository.save(cart);
		});
	}

	private CartDto getCartDto(Cart cart) {
		List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
		List<CartItemDto> dtos = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;

		for (CartItem item : items) {
			try {
				ProductDto product = productClient.getProduct(item.getProductId().toString());
				BigDecimal itemTotal = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));
				dtos.add(new CartItemDto(
					item.getProductId().toString(),
					product.name(),
					product.price(),
					item.getQuantity(),
					itemTotal
				));
				total = total.add(itemTotal);
			} catch (Exception ex) {
				log.warn("Failed to fetch product {} for cart item {}", item.getProductId(), item.getId());
			}
		}

		return new CartDto(cart.getId().toString(), cart.getUserId().toString(), dtos, total);
	}
}