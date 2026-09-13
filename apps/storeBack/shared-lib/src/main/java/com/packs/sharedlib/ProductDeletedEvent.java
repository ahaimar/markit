package com.packs.sharedlib;

import java.time.Instant;

public record ProductDeletedEvent(String eventId, String productId, Instant deletedAt) {
}