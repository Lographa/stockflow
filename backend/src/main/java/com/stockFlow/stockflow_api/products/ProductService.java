package com.stockFlow.stockflow_api.products;

import java.util.List;
import org.springframework.stereotype.Service;

import com.stockFlow.stockflow_api.movements.Movements;
import com.stockFlow.stockflow_api.movements.MovementsRepository;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.enums.MovementsDescriptionEnum;
import com.stockFlow.stockflow_api.movements.mapper.MovementsMapper;
import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.exception.SameProductException;
import com.stockFlow.stockflow_api.products.mapper.ProductMapper;

import jakarta.transaction.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MovementsRepository movementsRepository;
    private final ProductMapper productMapper;
    private final MovementsMapper movementsMapper;

    public ProductService(ProductRepository productRepository, MovementsRepository movementsRepository,
            ProductMapper productMapper, MovementsMapper movementsMapper) {
        this.productRepository = productRepository;
        this.movementsRepository = movementsRepository;
        this.productMapper = productMapper;
        this.movementsMapper = movementsMapper;
    }

    public List<ProductResponseAll> findAll() {
        return productRepository.findAll().stream().map(productMapper::toDtoAll).toList();
    }

    @Transactional
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toDto(product);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsByNameIgnoreCaseOrSkuIgnoreCase(
                request.name(),
                request.sku())) {
            throw new SameProductException(request.name(), request.sku());
        }

        Product produto = new Product();
        produto.setName(request.name());
        produto.setSku(request.sku());
        produto.setMin_stock(request.min_stock());
        produto.setPrice(request.price());
        produto.setQuantity(request.quantity());
        produto.setDescription(request.description());

        Product saved = productRepository.save(produto);

        Movements movimento = new Movements();
        movimento.setProduct(saved);
        movimento.setQuantity(request.quantity());
        movimento.setType(MovementTypeEnum.ENTRY);
        movimento.setDescription(MovementsDescriptionEnum.ENTRADA.getDescription());

        movementsRepository.save(movimento);

        return productMapper.toDto(saved);
    }

}
