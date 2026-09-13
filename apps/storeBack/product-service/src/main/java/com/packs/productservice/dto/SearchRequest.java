package com.packs.productservice.dto;

import java.math.BigDecimal;

public record SearchRequest(
	String query,
	String category,
	BigDecimal priceMin,
	BigDecimal priceMax,
	Boolean inStock,
	int page,
	int pageSize
) {
}