package com.packs.orderservice.controller;

import com.packs.orderservice.dto.OrderDto;
import com.packs.orderservice.dto.PlaceOrderRequest;
import com.packs.orderservice.service.OrderService;
import com.packs.sharedlib.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderController.class);

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderDto> placeOrder(
		@RequestHeader("X-User-Id") UUID userId,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestHeader(value = "X-Idempotency-Key", required = false) String legacyIdempotencyKey,
		@Valid @RequestBody PlaceOrderRequest request) {
		String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : legacyIdempotencyKey;
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request, userId, effectiveKey));
	}

	@GetMapping
	public ResponseEntity<com.packs.sharedlib.PageResponse<OrderDto>> listOrders(
		@RequestHeader("X-User-Id") UUID userId,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int pageSize) {
		return ResponseEntity.ok(orderService.listOrders(userId, page, pageSize));
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<OrderDto> getOrder(
		@RequestHeader("X-User-Id") UUID userId,
		@RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
		@PathVariable UUID orderId) {
		OrderDto order = orderService.getOrder(orderId);
		boolean isAdmin = rolesHeader != null && List.of(rolesHeader.split(",")).contains("ADMIN");
		if (!isAdmin && !order.userId().equals(userId.toString())) {
			throw new ApiException("ERR_FORBIDDEN", "Access denied for this order", HttpStatus.FORBIDDEN);
		}
		return ResponseEntity.ok(order);
	}

	@PutMapping("/{orderId}/status")
	public ResponseEntity<OrderDto> updateStatus(
		@RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
		@PathVariable UUID orderId,
		@RequestBody Map<String, String> body) {
		requireAdmin(rolesHeader);
		return ResponseEntity.ok(orderService.updateStatus(orderId, body.get("status")));
	}

	private void requireAdmin(String rolesHeader) {
		if (rolesHeader == null || !List.of(rolesHeader.split(",")).contains("ADMIN")) {
			log.warn("Rejected admin-only order status update: rolesHeader={}", rolesHeader);
			throw new com.packs.sharedlib.ApiException("ERR_FORBIDDEN", "ADMIN role required", org.springframework.http.HttpStatus.FORBIDDEN);
		}
	}
}