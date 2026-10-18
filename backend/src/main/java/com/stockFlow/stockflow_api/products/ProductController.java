package com.stockFlow.stockflow_api.products;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;
import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;
import com.stockFlow.stockflow_api.products.dto.UpdateProductRequest;

import jakarta.validation.Valid;

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

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping("/create")
    public ProductResponse postProduct(@Valid @RequestBody CreateProductRequest body) {
        return productService.createProduct(body);
    }

    @PutMapping("/{id}")
    public ProductResponse putProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest body) {
        return productService.updateProduct(id, body);
    }

    @GetMapping("/search")
    public List<ProductResponseAll> search(
            @RequestParam(required = false) List<Long> ids,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) String q) {

        var filter = new ProductSearchFilter(ids, createdFrom, createdTo, q);
        return productService.search(filter);
    }

}
