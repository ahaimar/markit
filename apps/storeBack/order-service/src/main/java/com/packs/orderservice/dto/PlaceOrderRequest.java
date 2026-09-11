package com.packs.orderservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlaceOrderRequest(
	@NotNull(message = "Cart id is required") UUID cartId,
	String shippingAddress
) {
}