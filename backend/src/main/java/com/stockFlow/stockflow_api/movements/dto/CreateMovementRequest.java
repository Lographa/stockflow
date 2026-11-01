package com.stockFlow.stockflow_api.movements.dto;

import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Entrada/saída de estoque. O cliente nunca envia o saldo. {@code description}
 * é opcional (ausente/em branco → descrição padrão do tipo).
 */
public record CreateMovementRequest(
        @NotNull(message = "O produto é obrigatório") Long product_id,
        @NotNull(message = "O tipo é obrigatório (ENTRY ou EXIT)") MovementTypeEnum type,
        @NotNull(message = "A quantidade é obrigatória") @Positive(message = "A quantidade deve ser maior que zero") Integer quantity,
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres") String description) {

}
