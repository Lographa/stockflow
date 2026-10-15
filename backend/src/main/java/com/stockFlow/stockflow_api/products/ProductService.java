package com.stockFlow.stockflow_api.products;

import java.util.List;

import org.springframework.stereotype.Service;

import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.mapper.ProductMapper;

import jakarta.transaction.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    public List<ProductResponseAll> findAll() {
        System.out.println("chegou aqui");
        return productRepository.findAll().stream().map(productMapper::toDtoAll).toList();
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        try {
            Product produto = new Product();
            produto.setName(request.name());
            produto.setSku(request.sku());
            produto.setMin_stock(request.min_stock());
            produto.setPrice(request.price());
            produto.setQuantity(request.quantity());
            produto.setDescription(request.description());

            Product saved = productRepository.save(produto);
            return productMapper.toDto(saved);

        } catch (Exception e) {
            throw new IllegalStateException("Erro ao criar transferencia");
        }
    }

}
