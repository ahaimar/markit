package com.packs.orderservice.controller;

import com.packs.orderservice.dto.CartAddRequest;
import com.packs.orderservice.dto.CartDto;
import com.packs.orderservice.dto.CartRemoveRequest;
import com.packs.orderservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@PostMapping("/cart/add")
	public ResponseEntity<CartDto> addItem(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CartAddRequest request) {
		return ResponseEntity.ok(cartService.addItem(request, userId));
	}

	@GetMapping("/cart")
	public ResponseEntity<CartDto> getCart(@RequestHeader("X-User-Id") UUID userId) {
		return ResponseEntity.ok(cartService.getCart(userId));
	}

	@PostMapping("/cart/remove")
	public ResponseEntity<CartDto> removeItem(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CartRemoveRequest request) {
		cartService.removeItem(request.productId(), userId);
		return ResponseEntity.ok(cartService.getCart(userId));
	}

	@PostMapping("/cart/clear")
	public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Id") UUID userId) {
		cartService.clearCart(userId);
		return ResponseEntity.noContent().build();
	}
}