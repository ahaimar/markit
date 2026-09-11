package com.packs.sharedlib;

import java.math.BigDecimal;

public record ProductDto(
	String id,
	String name,
	String description,
	BigDecimal price,
	String category,
	int stockQuantity
) {
}