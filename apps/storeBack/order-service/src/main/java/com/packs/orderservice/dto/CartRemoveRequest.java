package com.packs.orderservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartRemoveRequest(@NotNull UUID productId) {
}