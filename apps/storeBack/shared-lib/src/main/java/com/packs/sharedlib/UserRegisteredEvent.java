package com.packs.sharedlib;

import java.time.Instant;

public record UserRegisteredEvent(String eventId, String userId, String email, String name, Instant registeredAt) {
}