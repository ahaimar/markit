package com.packs.sharedlib;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCancelledEvent(String eventId, String orderId, String userId, BigDecimal totalPrice, String reason, Instant cancelledAt) {
}
