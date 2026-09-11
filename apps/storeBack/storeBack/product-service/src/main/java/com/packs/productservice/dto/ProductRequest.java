package com.packs.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequest(
	@NotBlank(message = "Name is required")
	String name,
	String description,
	@NotNull(message = "Price is required")
	BigDecimal price,
	@NotBlank(message = "Category is required")
	String category,
	@PositiveOrZero(message = "Stock quantity must be >= 0")
	int stockQuantity
) {
}