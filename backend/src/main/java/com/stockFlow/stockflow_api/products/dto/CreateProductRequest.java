package com.stockFlow.stockflow_api.products.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
                @NotBlank(message = "O sku é obrigatório") @Size(max = 50, message = "O sku deve ter no máximo 50 caracteres") String sku,
                @NotBlank(message = "O nome é obrigatório") @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres") String name,
                @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String description,
                @NotNull(message = "O preço é obrigatório") @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") @Digits(integer = 8, fraction = 2, message = "O preço deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal price,
                @NotNull(message = "A quantidade é obrigatória") @PositiveOrZero(message = "A quantidade não pode ser negativa") Integer quantity,
                @NotNull(message = "O estoque mínimo é obrigatório") @PositiveOrZero(message = "O estoque mínimo não pode ser negativo") Integer min_stock) {

}
