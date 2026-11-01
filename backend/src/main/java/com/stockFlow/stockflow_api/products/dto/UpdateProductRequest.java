package com.stockFlow.stockflow_api.products.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Todos os campos são opcionais; se informados, não podem ser vazios/em branco
 * (strings) nem negativos (números). O padrão (?s).*\S.* exige ao menos um
 * caractere não-espaço e aceita null (campo não informado).
 */
public record UpdateProductRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "O sku não pode ser vazio") @Size(max = 50, message = "O sku deve ter no máximo 50 caracteres") String sku,
        @Pattern(regexp = "(?s).*\\S.*", message = "O nome não pode ser vazio") @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres") String name,
        @Pattern(regexp = "(?s).*\\S.*", message = "A descrição não pode ser vazia") @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String description,
        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") @Digits(integer = 8, fraction = 2, message = "O preço deve ter até 8 dígitos inteiros e 2 decimais") BigDecimal price,
        @PositiveOrZero(message = "A quantidade não pode ser negativa") Integer quantity,
        @PositiveOrZero(message = "O estoque mínimo não pode ser negativo") Integer min_stock) {

}
