package com.stockFlow.stockflow_api.products.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Produto não encontrado: " + id);
    }

}
