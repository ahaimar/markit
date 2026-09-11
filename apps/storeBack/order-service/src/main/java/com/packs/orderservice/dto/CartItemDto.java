package com.packs.orderservice.dto;

import java.math.BigDecimal;

public record CartItemDto(
	String productId,
	String productName,
	BigDecimal unitPrice,
	int quantity,
	BigDecimal totalPrice
) {
}