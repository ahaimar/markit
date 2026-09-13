package com.packs.sharedlib;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductUpdatedEvent(
	String eventId,
	String productId,
	String name,
	String description,
	BigDecimal price,
	String category,
	int stockQuantity,
	Instant updatedAt
) {
}