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
import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;
import com.stockFlow.stockflow_api.products.dto.UpdateProductRequest;
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

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (productRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new SameProductException("Já existe produto cadastrado com esse nome");
        }
        if (productRepository.existsBySkuIgnoreCaseAndIdNot(request.sku(), id)) {
            throw new SameProductException("Já existe produto cadastrado com esse sku");
        }
        if (request.sku() != null) {
            product.setSku(request.sku());
        }

        if (request.name() != null) {
            product.setName(request.name());
        }

        if (request.description() != null) {
            product.setDescription(request.description());
        }

        if (request.price() != null) {
            product.setPrice(request.price());
        }

        if (request.quantity() != null) {
            product.setQuantity(request.quantity());
        }

        if (request.min_stock() != null) {
            product.setMin_stock(request.min_stock());
        }

        return productMapper.toDto(productRepository.save(product));
    }

    @Transactional
    public String deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }

        List<Movements> movimentos = movementsRepository.findByProductId(id);
        movementsRepository.deleteAll(movimentos);
        movementsRepository.flush();
        productRepository.deleteById(id);

        return "Produto deletado com sucesso";
    }

    public List<ProductResponseAll> search(ProductSearchFilter filter) {
        return productRepository.findAll(ProductSpecifications.from(filter))
                .stream()
                .map(productMapper::toDtoAll)
                .toList();
    }

}
