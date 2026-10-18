package com.stockFlow.stockflow_api.products;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.*;

import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;

import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("")
    public List<ProductResponseAll> getAll() {
        return productService.findAll();
    }

    @GetMapping("/id")
    public ProductResponse getById(@PathParam("id") Long id) {
        return productService.findById(id);
    }

    @PostMapping("/create")
    public ProductResponse postProduct(@Valid @RequestBody CreateProductRequest body) {
        return productService.createProduct(body);
    }

}
