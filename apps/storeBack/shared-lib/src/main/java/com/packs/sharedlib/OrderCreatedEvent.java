package com.packs.sharedlib;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(String orderId, String userId, BigDecimal totalPrice, Instant createdAt) {
}