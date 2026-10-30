package com.stockFlow.stockflow_api.movements.dto;

import java.time.LocalDateTime;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

/** Filtros opcionais do histórico (GET /movements). */
public record MovementSearchFilter(
        Long productId,
        MovementTypeEnum type,
        LocalDateTime from,
        LocalDateTime to) {

}
