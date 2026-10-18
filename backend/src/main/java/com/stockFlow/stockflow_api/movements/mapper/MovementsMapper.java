package com.stockFlow.stockflow_api.movements.mapper;

import org.springframework.stereotype.Component;

import com.stockFlow.stockflow_api.movements.Movements;
import com.stockFlow.stockflow_api.movements.dto.MovimentsDtoAll;

@Component
public class MovementsMapper {

    public MovimentsDtoAll toDtoAll(Movements movement) {
        if (movement == null)
            return null;

        return new MovimentsDtoAll(movement.getId(), movement.getProduct(), movement.getType(),
                movement.getQuantity(),
                movement.getDescription(),
                movement.getCreatedAt());
    }

}
