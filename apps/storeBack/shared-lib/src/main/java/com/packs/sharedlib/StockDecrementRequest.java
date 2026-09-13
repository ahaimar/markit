package com.packs.sharedlib;

import jakarta.validation.constraints.Positive;

public record StockDecrementRequest(@Positive int quantity) {
}