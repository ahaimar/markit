package com.packs.productservice.dto;

import jakarta.validation.constraints.Positive;

public record StockDecrementRequest(@Positive int quantity) {
}