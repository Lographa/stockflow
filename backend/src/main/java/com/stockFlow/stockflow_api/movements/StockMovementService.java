package com.stockFlow.stockflow_api.movements;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockFlow.stockflow_api.movements.dto.CreateMovementRequest;
import com.stockFlow.stockflow_api.movements.dto.MovementResponse;
import com.stockFlow.stockflow_api.movements.dto.MovementSearchFilter;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.enums.MovementsDescriptionEnum;
import com.stockFlow.stockflow_api.movements.exception.InsufficientStockException;
import com.stockFlow.stockflow_api.movements.mapper.MovementsMapper;
import com.stockFlow.stockflow_api.products.Product;
import com.stockFlow.stockflow_api.products.ProductRepository;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;

/**
 * Único ponto do sistema que altera {@code Product.quantity} (T-03). Toda
 * mudança de saldo gera, na mesma transação, um registro de movimentação. O
 * MySQL é a fonte da verdade (lock pessimista, T-04); Redis não participa.
 *
 * Não depende de {@code ProductService} (evita ciclo de beans).
 */
@Service
public class StockMovementService {

    private static final Sort HISTORY_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ProductRepository productRepository;
    private final MovementsRepository movementsRepository;
    private final MovementsMapper movementsMapper;

    public StockMovementService(ProductRepository productRepository, MovementsRepository movementsRepository,
            MovementsMapper movementsMapper) {
        this.productRepository = productRepository;
        this.movementsRepository = movementsRepository;
        this.movementsMapper = movementsMapper;
    }

    /**
     * Registra entrada/saída via API. Carrega o produto ativo com lock
     * (SELECT ... FOR UPDATE) antes de validar o saldo. Produto inexistente ou
     * excluído → {@link ProductNotFoundException}; saída maior que o saldo →
     * {@link InsufficientStockException} (nada é alterado nem gravado).
     */
    @Transactional
    public MovementResponse register(CreateMovementRequest request) {
        Product product = productRepository.findActiveByIdForUpdate(request.product_id())
                .orElseThrow(() -> new ProductNotFoundException(request.product_id()));

        Movements movement = apply(product, request.type(), request.quantity(), request.description());

        return movementsMapper.toResponse(movement, product.getQuantity());
    }

    /**
     * Operação interna reutilizável (API, criação e ajuste de PUT): aplica a
     * movimentação sobre um produto JÁ carregado — quem chama é responsável por
     * tê-lo carregado com lock (ou por ele ser recém-criado) — atualiza o saldo
     * da entidade e grava a movimentação. Exige transação em andamento, para que
     * saldo e movimentação nunca sejam confirmados separadamente.
     *
     * @param description nula/em branco → descrição padrão do tipo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Movements apply(Product product, MovementTypeEnum type, Integer quantity, String description) {
        if (product == null || type == null) {
            throw new IllegalArgumentException("Produto e tipo da movimentação são obrigatórios");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("A quantidade da movimentação deve ser maior que zero");
        }

        int current = product.getQuantity();
        int newBalance;
        if (type == MovementTypeEnum.EXIT) {
            if (current < quantity) {
                throw new InsufficientStockException(product.getName(), current, quantity);
            }
            newBalance = current - quantity;
        } else {
            newBalance = Math.addExact(current, quantity);
        }

        Movements movement = new Movements();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setDescription(resolveDescription(type, description));

        product.setQuantity(newBalance);
        return movementsRepository.save(movement);
    }

    /**
     * Histórico (GET /movements). Com {@code product_id}: o produto deve existir
     * (ativo OU excluído), senão 404. Montagem do DTO dentro da transação
     * readOnly (Movements.product é LAZY; a Specification já faz fetch join).
     */
    @Transactional(readOnly = true)
    public List<MovementResponse> search(MovementSearchFilter filter) {
        if (filter.productId() != null && !productRepository.existsById(filter.productId())) {
            throw new ProductNotFoundException(filter.productId());
        }
        return movementsRepository.findAll(MovementsSpecifications.from(filter), HISTORY_ORDER)
                .stream()
                .map(movementsMapper::toResponse)
                .toList();
    }

    private String resolveDescription(MovementTypeEnum type, String description) {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return (type == MovementTypeEnum.ENTRY ? MovementsDescriptionEnum.ENTRADA : MovementsDescriptionEnum.SAIDA)
                .getDescription();
    }
}
