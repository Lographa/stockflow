package com.stockFlow.stockflow_api.products.exception;

public class SameProductException extends RuntimeException {

    public SameProductException(String name, String sku) {
        super("Nome do produto: " + name + " ou sku: " + sku + " já tem no sistema.");
    }

    public SameProductException(String message) {
        super(message);
    }
}
