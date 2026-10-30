package com.stockFlow.stockflow_api.movements.mapper;

import org.springframework.stereotype.Component;

import com.stockFlow.stockflow_api.movements.Movements;
import com.stockFlow.stockflow_api.movements.dto.MovementResponse;
import com.stockFlow.stockflow_api.products.Product;

@Component
public class MovementsMapper {

    /** Para consultas (GET): {@code stock_after} = null. */
    public MovementResponse toResponse(Movements movement) {
        return toResponse(movement, null);
    }

    /**
     * Acessa {@code movement.getProduct()} (LAZY): chamar dentro de transação ou
     * com o produto já carregado (fetch join).
     */
    public MovementResponse toResponse(Movements movement, Integer stockAfter) {
        if (movement == null)
            return null;

        Product product = movement.getProduct();
        return new MovementResponse(
                movement.getId(),
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDeletedAt() != null,
                movement.getType(),
                movement.getQuantity(),
                movement.getDescription(),
                stockAfter,
                movement.getCreatedAt());
    }

}
