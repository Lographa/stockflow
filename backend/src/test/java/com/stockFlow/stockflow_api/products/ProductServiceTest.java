package com.stockFlow.stockflow_api.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockFlow.stockflow_api.movements.Movements;
import com.stockFlow.stockflow_api.movements.MovementsRepository;
import com.stockFlow.stockflow_api.movements.StockMovementService;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.mapper.MovementsMapper;
import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;
import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;
import com.stockFlow.stockflow_api.products.dto.UpdateProductRequest;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.exception.SameProductException;
import com.stockFlow.stockflow_api.products.mapper.ProductMapper;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private MovementsRepository movementsRepository;

    private StockMovementService stockMovementService;
    private ProductService service;

    @BeforeEach
    void setUp() {
        // StockMovementService real (spy) sobre repositórios mockados: valida a regra única (T-03).
        stockMovementService = spy(new StockMovementService(productRepository, movementsRepository, new MovementsMapper()));
        service = new ProductService(productRepository, stockMovementService, new ProductMapper());
    }

    private static CreateProductRequest createRequest(int quantity) {
        return new CreateProductRequest("MOUSE-001", "Mouse Gamer", "desc", new BigDecimal("129.90"), quantity, 10);
    }

    private static Product persisted(Long id, int quantity, int minStock) {
        Product p = new Product();
        p.setId(id);
        p.setSku("MOUSE-001");
        p.setName("Mouse Gamer");
        p.setPrice(new BigDecimal("129.90"));
        p.setQuantity(quantity);
        p.setMin_stock(minStock);
        p.setCreated_at(LocalDateTime.of(2026, 10, 30, 10, 0));
        p.setUpdated_at(LocalDateTime.of(2026, 10, 30, 10, 0));
        return p;
    }

    private static Product withId(Product product, Long id) {
        product.setId(id);
        product.setCreated_at(LocalDateTime.of(2026, 10, 30, 10, 0));
        product.setUpdated_at(LocalDateTime.of(2026, 10, 30, 10, 0));
        return product;
    }

    // ---------- create ----------

    @Test
    void create_quantidadeZero_naoCriaMovimentacao() {
        when(productRepository.existsByNameIgnoreCaseOrSkuIgnoreCase("Mouse Gamer", "MOUSE-001")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        ProductResponse response = service.createProduct(createRequest(0));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.quantity()).isZero();
        assertThat(response.low_stock()).isTrue();
        assertThat(response.created_at()).isNotNull();
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void create_quantidadeMaiorQueZero_criaUmaMovimentacaoEntry() {
        when(productRepository.existsByNameIgnoreCaseOrSkuIgnoreCase("Mouse Gamer", "MOUSE-001")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        ProductResponse response = service.createProduct(createRequest(25));

        // movimentação inicial passa pelo StockMovementService (regra única)
        verify(stockMovementService).apply(any(Product.class), eq(MovementTypeEnum.ENTRY), eq(25),
                eq("Entrada de estoque"));
        ArgumentCaptor<Movements> captor = ArgumentCaptor.forClass(Movements.class);
        verify(movementsRepository, times(1)).save(captor.capture());
        Movements movement = captor.getValue();
        assertThat(movement.getType()).isEqualTo(MovementTypeEnum.ENTRY);
        assertThat(movement.getQuantity()).isEqualTo(25);
        assertThat(movement.getProduct().getId()).isEqualTo(1L);
        assertThat(movement.getDescription()).isEqualTo("Entrada de estoque");
        assertThat(response.quantity()).isEqualTo(25);
        assertThat(response.low_stock()).isFalse();
    }

    @Test
    void create_nomeOuSkuDuplicado_lancaSameProductException_inclusiveDeExcluido() {
        // existsBy... não filtra deleted_at: produto excluído continua reservando nome/SKU.
        when(productRepository.existsByNameIgnoreCaseOrSkuIgnoreCase("Mouse Gamer", "MOUSE-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createProduct(createRequest(5)))
                .isInstanceOf(SameProductException.class);

        verify(productRepository, never()).saveAndFlush(any());
        verify(productRepository, never()).save(any());
        verifyNoInteractions(movementsRepository);
    }

    // ---------- update ----------

    @Test
    void update_produtoExcluidoOuInexistente_lancaNotFound() {
        when(productRepository.findActiveByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(9L, new UpdateProductRequest("X", null, null, null, null, null)))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_nomeDuplicadoInclusiveDeExcluido_lancaSameProduct() {
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(persisted(1L, 5, 1)));
        when(productRepository.existsByNameIgnoreCaseAndIdNot("Outro", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProduct(1L, new UpdateProductRequest(null, "Outro", null, null, null, null)))
                .isInstanceOf(SameProductException.class);
    }

    @Test
    void update_skuDuplicado_lancaSameProduct() {
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(persisted(1L, 5, 1)));
        when(productRepository.existsBySkuIgnoreCaseAndIdNot("DUP", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProduct(1L, new UpdateProductRequest("DUP", null, null, null, null, null)))
                .isInstanceOf(SameProductException.class);
    }

    @Test
    void update_aplicaApenasCamposInformados_eRetornaRespostaCompleta() {
        Product existing = persisted(1L, 5, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse response = service.updateProduct(1L,
                new UpdateProductRequest(null, "Novo nome", null, BigDecimal.ZERO, null, null));

        assertThat(response.name()).isEqualTo("Novo nome");
        assertThat(response.sku()).isEqualTo("MOUSE-001");
        assertThat(response.price()).isEqualByComparingTo("0");
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.low_stock()).isTrue();
        // nulos não disparam checagem de unicidade
        verify(productRepository, never()).existsBySkuIgnoreCaseAndIdNot(any(), anyLong());
    }

    // ---------- update: ajuste de estoque (D-1) ----------

    private Movements captureSingleMovement() {
        ArgumentCaptor<Movements> captor = ArgumentCaptor.forClass(Movements.class);
        verify(movementsRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void update_quantityAumenta_geraEntryDaDiferenca() {
        Product existing = persisted(1L, 25, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse response = service.updateProduct(1L, new UpdateProductRequest(null, null, null, null, 30, null));

        Movements movement = captureSingleMovement();
        assertThat(movement.getType()).isEqualTo(MovementTypeEnum.ENTRY);
        assertThat(movement.getQuantity()).isEqualTo(5);
        assertThat(movement.getDescription()).isEqualTo("Atualizado estoque");
        assertThat(movement.getProduct()).isSameAs(existing);
        assertThat(response.quantity()).isEqualTo(30);
        verify(stockMovementService).apply(existing, MovementTypeEnum.ENTRY, 5, "Atualizado estoque");
    }

    @Test
    void update_quantityDiminui_geraExitDaDiferenca() {
        Product existing = persisted(1L, 25, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse response = service.updateProduct(1L, new UpdateProductRequest(null, null, null, null, 20, null));

        Movements movement = captureSingleMovement();
        assertThat(movement.getType()).isEqualTo(MovementTypeEnum.EXIT);
        assertThat(movement.getQuantity()).isEqualTo(5);
        assertThat(movement.getDescription()).isEqualTo("Atualizado estoque");
        assertThat(response.quantity()).isEqualTo(20);
    }

    @Test
    void update_quantityDiminuiParaZero_geraExitDoSaldoTotal() {
        Product existing = persisted(1L, 25, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        service.updateProduct(1L, new UpdateProductRequest(null, null, null, null, 0, null));

        Movements movement = captureSingleMovement();
        assertThat(movement.getType()).isEqualTo(MovementTypeEnum.EXIT);
        assertThat(movement.getQuantity()).isEqualTo(25);
        assertThat(existing.getQuantity()).isZero();
    }

    @Test
    void update_quantityIgualAoAtual_naoGeraMovimentacao() {
        Product existing = persisted(1L, 25, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse response = service.updateProduct(1L, new UpdateProductRequest(null, null, null, null, 25, null));

        assertThat(response.quantity()).isEqualTo(25);
        verifyNoInteractions(movementsRepository);
        verify(stockMovementService, never()).apply(any(), any(), any(), any());
    }

    @Test
    void update_semQuantity_naoGeraMovimentacao_eMantemSaldo() {
        Product existing = persisted(1L, 25, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse response = service.updateProduct(1L,
                new UpdateProductRequest(null, "Novo nome", null, null, null, 3));

        assertThat(response.quantity()).isEqualTo(25);
        assertThat(response.min_stock()).isEqualTo(3);
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void update_nomeDuplicadoComQuantity_naoGeraMovimentacao() {
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(persisted(1L, 25, 10)));
        when(productRepository.existsByNameIgnoreCaseAndIdNot("Outro", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProduct(1L, new UpdateProductRequest(null, "Outro", null, null, 40, null)))
                .isInstanceOf(SameProductException.class);

        verifyNoInteractions(movementsRepository);
    }

    // ---------- delete (soft) ----------

    @Test
    void delete_apenasDefineDeletedAt_enaoRemoveNada() {
        Product existing = persisted(1L, 5, 10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        LocalDateTime before = LocalDateTime.now();

        service.deleteProduct(1L);

        assertThat(existing.getDeletedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(existing.getQuantity()).isEqualTo(5);
        verify(productRepository).save(existing);
        verify(productRepository).findActiveByIdForUpdate(1L);
        // nenhuma outra chamada (nenhum delete físico) no repositório de produtos
        verifyNoMoreInteractions(productRepository);
        // nem leitura/remoção de movimentações
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void delete_inexistenteOuJaExcluido_lancaNotFound() {
        when(productRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProduct(7L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
        verifyNoInteractions(movementsRepository);
    }

    // ---------- leitura (somente ativos) ----------

    @Test
    void findById_excluido_lancaNotFound() {
        when(productRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(3L)).isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void findById_ativo_retornaRespostaCompleta() {
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(persisted(1L, 25, 10)));

        ProductResponse response = service.findById(1L);

        assertThat(response.sku()).isEqualTo("MOUSE-001");
        assertThat(response.name()).isEqualTo("Mouse Gamer");
        assertThat(response.min_stock()).isEqualTo(10);
    }

    @Test
    void findAll_semProdutos_retornaListaVazia() {
        when(productRepository.findAllByDeletedAtIsNull()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
        verify(productRepository, never()).findAll();
    }

    @Test
    void findAll_mapeiaSkuNameQuantityMinStockCorretamente() {
        when(productRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(persisted(1L, 8, 10)));

        ProductResponseAll item = service.findAll().get(0);

        assertThat(item.sku()).isEqualTo("MOUSE-001");
        assertThat(item.name()).isEqualTo("Mouse Gamer");
        assertThat(item.quantity()).isEqualTo(8);
        assertThat(item.min_stock()).isEqualTo(10);
        assertThat(item.low_stock()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_usaSpecification_eMapeiaResultado() {
        when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(persisted(1L, 8, 10)));

        List<ProductResponseAll> result = service.search(new ProductSearchFilter(null, null, null, null, true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).low_stock()).isTrue();
    }
}
