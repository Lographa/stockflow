package com.stockFlow.stockflow_api.movements.exception;

/**
 * Saída maior que o saldo disponível (→ HTTP 409). Lançada antes de qualquer
 * alteração de saldo ou gravação de movimentação.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName, int available, int requested) {
        super("Estoque insuficiente para o produto '" + productName + "': saldo disponível " + available
                + ", quantidade solicitada " + requested);
    }
}
