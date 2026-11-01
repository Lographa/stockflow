package com.stockFlow.stockflow_api.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo padrão de erro. {@code field_errors} só aparece em 400 de validação
 * (campo → mensagem); nos demais casos é omitido do JSON.
 */
public record ErrorResponseDTO(String error,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> field_errors) {

    public ErrorResponseDTO(String error, String message) {
        this(error, message, null);
    }
}
