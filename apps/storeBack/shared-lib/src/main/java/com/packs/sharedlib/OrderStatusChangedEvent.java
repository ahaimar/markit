package com.packs.sharedlib;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderStatusChangedEvent(String eventId, String orderId, String userId, BigDecimal totalPrice, String fromStatus, String toStatus, Instant changedAt) {
}
