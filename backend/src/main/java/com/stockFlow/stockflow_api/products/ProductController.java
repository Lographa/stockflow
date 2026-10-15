package com.stockFlow.stockflow_api.products;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductService productService;

    private ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("")
    public List<ProductResponseAll> getAll() {
        return productService.findAll();
    }

    @PostMapping("/create")
    public ProductResponse postMethodName(@RequestBody CreateProductRequest body) {
        return productService.createProduct(body);
    }

}
