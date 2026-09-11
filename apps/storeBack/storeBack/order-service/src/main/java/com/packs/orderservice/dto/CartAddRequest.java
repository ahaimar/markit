package com.packs.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartAddRequest(
	@NotNull(message = "Product id is required") UUID productId,
	@Min(value = 1, message = "Quantity must be at least 1") int quantity
) {
}