package com.packs.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
	String orderId,
	String userId,
	String status,
	BigDecimal totalPrice,
	String shippingAddress,
	Instant createdAt,
	List<OrderItemDto> items
) {
}