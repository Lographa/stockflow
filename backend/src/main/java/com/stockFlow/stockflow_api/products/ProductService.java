package com.stockFlow.stockflow_api.products;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockFlow.stockflow_api.movements.StockMovementService;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.enums.MovementsDescriptionEnum;
import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;
import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;
import com.stockFlow.stockflow_api.products.dto.UpdateProductRequest;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.exception.SameProductException;
import com.stockFlow.stockflow_api.products.mapper.ProductMapper;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, StockMovementService stockMovementService,
            ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.stockMovementService = stockMovementService;
        this.productMapper = productMapper;
    }

    /** Somente produtos ativos (não excluídos). */
    @Transactional(readOnly = true)
    public List<ProductResponseAll> findAll() {
        return productRepository.findAllByDeletedAtIsNull().stream().map(productMapper::toDtoAll).toList();
    }

    /** Produto inexistente ou excluído → {@link ProductNotFoundException}. */
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toDto(product);
    }

    /**
     * Cria o produto e, somente se quantity > 0, a movimentação ENTRY inicial —
     * ambos na mesma transação. O produto é persistido com saldo 0 e o saldo
     * inicial é aplicado pelo {@link StockMovementService} (única fonte da regra,
     * T-03). Nome/SKU duplicados (inclusive de produto excluído) →
     * {@link SameProductException}.
     */
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
        produto.setQuantity(0); // saldo inicial só via StockMovementService (abaixo)
        produto.setDescription(request.description());

        // saveAndFlush: força o INSERT agora; created_at/updated_at (gerados pelo MySQL)
        // são relidos pelo Hibernate (@Generated) e já saem preenchidos na resposta.
        Product saved = productRepository.saveAndFlush(produto);

        if (request.quantity() > 0) {
            stockMovementService.apply(saved, MovementTypeEnum.ENTRY, request.quantity(),
                    MovementsDescriptionEnum.ENTRADA.getDescription());
            // novo flush: grava o saldo e relê updated_at antes de montar a resposta.
            saved = productRepository.saveAndFlush(saved);
        }

        return productMapper.toDto(saved);
    }

    /**
     * Atualiza o produto ativo (carregado com lock, T-04). Se {@code quantity} for
     * informado e diferente do saldo, a diferença é aplicada pelo
     * {@link StockMovementService} como ajuste (D-1): aumento → ENTRY, redução →
     * EXIT, descrição ATUALIZACAO, na mesma transação. Igual ao atual → nenhuma
     * movimentação. O saldo nunca é gravado diretamente.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (request.name() != null && productRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new SameProductException("Já existe produto cadastrado com esse nome");
        }
        if (request.sku() != null && productRepository.existsBySkuIgnoreCaseAndIdNot(request.sku(), id)) {
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
            adjustStock(product, request.quantity());
        }

        if (request.min_stock() != null) {
            product.setMin_stock(request.min_stock());
        }

        // saveAndFlush: updated_at (ON UPDATE do MySQL) é relido antes de montar a resposta.
        return productMapper.toDto(productRepository.saveAndFlush(product));
    }

    /** Ajuste (D-1): diferença entre o saldo atual e o novo vira ENTRY/EXIT. */
    private void adjustStock(Product product, int newQuantity) {
        int current = product.getQuantity();
        if (newQuantity > current) {
            stockMovementService.apply(product, MovementTypeEnum.ENTRY, newQuantity - current,
                    MovementsDescriptionEnum.ATUALIZACAO.getDescription());
        } else if (newQuantity < current) {
            stockMovementService.apply(product, MovementTypeEnum.EXIT, current - newQuantity,
                    MovementsDescriptionEnum.ATUALIZACAO.getDescription());
        }
    }

    /**
     * Soft delete: apenas define deleted_at = agora. Não remove o produto nem
     * as movimentações (histórico preservado). Trava a linha (T-04) para que
     * nenhuma movimentação concorrente entre depois da exclusão.
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    /** Somente produtos ativos que atendem aos filtros. */
    @Transactional(readOnly = true)
    public List<ProductResponseAll> search(ProductSearchFilter filter) {
        return productRepository.findAll(ProductSpecifications.from(filter))
                .stream()
                .map(productMapper::toDtoAll)
                .toList();
    }

}
