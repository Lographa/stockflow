package com.stockFlow.stockflow_api.products.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponseAll(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int quantity,
        int min_stock,
        LocalDateTime created_at,
        LocalDateTime updated_at) {

}
