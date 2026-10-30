package com.stockFlow.stockflow_api.products.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resposta completa de produto (GET /product/{id}, POST, PUT).
 * Mesmos campos de {@link ProductResponseAll}.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int quantity,
        int min_stock,
        boolean low_stock,
        LocalDateTime created_at,
        LocalDateTime updated_at) {

}
