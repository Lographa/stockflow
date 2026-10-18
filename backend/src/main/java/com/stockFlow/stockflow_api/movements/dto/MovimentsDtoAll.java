package com.stockFlow.stockflow_api.movements.dto;

import java.time.LocalDateTime;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.products.Product;

public record MovimentsDtoAll(
                Long id,
                Product product,
                MovementTypeEnum type,
                Integer quantity,
                String description,
                LocalDateTime created_at) {

}
