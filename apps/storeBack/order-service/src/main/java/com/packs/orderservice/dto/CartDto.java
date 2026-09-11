package com.packs.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(String cartId, String userId, List<CartItemDto> items, BigDecimal totalPrice) {

	public static CartDto empty(String userId) {
		return new CartDto(null, userId, List.of(), BigDecimal.ZERO);
	}
}