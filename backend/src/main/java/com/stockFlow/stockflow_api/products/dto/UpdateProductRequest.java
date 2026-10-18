package com.stockFlow.stockflow_api.products.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductRequest(
        String sku,
        String name,
        String description,
        @Positive BigDecimal price,
        @PositiveOrZero Integer quantity,
        @PositiveOrZero Integer min_stock) {

}
