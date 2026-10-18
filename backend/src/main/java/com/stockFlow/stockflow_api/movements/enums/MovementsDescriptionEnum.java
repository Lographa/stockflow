package com.stockFlow.stockflow_api.movements.enums;

public enum MovementsDescriptionEnum {
    ENTRADA("Entrada de estoque"),
    SAIDA("Saída de estoque"),
    ATUALIZACAO("Atualizado estoque");

    private final String description;

    MovementsDescriptionEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
