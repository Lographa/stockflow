package com.stockFlow.stockflow_api.movements.dto;

import java.time.LocalDateTime;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

/**
 * Contrato 4.3. {@code stock_after} só vem preenchido no POST (saldo após a
 * operação); nos GET é {@code null}.
 */
public record MovementResponse(
        Long id,
        Long product_id,
        String product_sku,
        String product_name,
        boolean product_deleted,
        MovementTypeEnum type,
        Integer quantity,
        String description,
        Integer stock_after,
        LocalDateTime created_at) {

}
