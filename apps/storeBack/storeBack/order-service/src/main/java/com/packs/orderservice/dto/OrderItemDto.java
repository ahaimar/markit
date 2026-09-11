package com.packs.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemDto(
	String productId,
	String productName,
	int quantity,
	BigDecimal priceAtPurchase
) {
}