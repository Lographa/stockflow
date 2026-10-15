package com.stockFlow.stockflow_api.products.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String name, Long id) {
        super("Produto não encontrado: " + name + " Id: " + id);
    }

}
