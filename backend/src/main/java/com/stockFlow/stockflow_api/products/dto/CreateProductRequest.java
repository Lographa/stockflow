package com.stockFlow.stockflow_api.products.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProductRequest(
                @NotBlank @NotNull String sku,
                @NotNull String name,
                String description,
                @NotNull @Positive BigDecimal price,
                @NotNull int quantity,
                @NotNull int min_stock) {

}
