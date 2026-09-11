package com.packs.sharedlib;

import java.util.Set;

public enum OrderStatus {

	PENDING,
	CONFIRMED,
	SHIPPED,
	DELIVERED,
	CANCELLED;

	private static final java.util.Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = java.util.Map.of(
		PENDING, Set.of(CONFIRMED, CANCELLED),
		CONFIRMED, Set.of(SHIPPED, CANCELLED),
		SHIPPED, Set.of(DELIVERED),
		DELIVERED, Set.of(),
		CANCELLED, Set.of()
	);

	public boolean canTransitionTo(OrderStatus target) {
		return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
	}
}
