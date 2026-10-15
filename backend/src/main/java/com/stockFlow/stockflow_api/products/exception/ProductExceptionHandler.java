package com.stockFlow.stockflow_api.products.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.stockFlow.stockflow_api.exception.ErrorResponseDTO;

@RestControllerAdvice(basePackages = "com.stockFlow.stockflow_api.products")
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDTO("produto não encontrado", ex.getMessage()));
    }

}
