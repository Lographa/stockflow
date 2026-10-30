package com.stockFlow.stockflow_api.movements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.stockFlow.stockflow_api.movements.dto.CreateMovementRequest;
import com.stockFlow.stockflow_api.movements.dto.MovementResponse;
import com.stockFlow.stockflow_api.movements.dto.MovementSearchFilter;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.exception.InsufficientStockException;
import com.stockFlow.stockflow_api.movements.mapper.MovementsMapper;
import com.stockFlow.stockflow_api.products.Product;
import com.stockFlow.stockflow_api.products.ProductRepository;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private MovementsRepository movementsRepository;

    private StockMovementService service;

    @BeforeEach
    void setUp() {
        service = new StockMovementService(productRepository, movementsRepository, new MovementsMapper());
    }

    private static Product product(int quantity) {
        Product p = new Product();
        p.setId(1L);
        p.setSku("MOUSE-001");
        p.setName("Mouse Gamer");
        p.setQuantity(quantity);
        p.setMin_stock(10);
        return p;
    }

    /** save devolve a própria movimentação, simulando id/created_at gerados pelo banco. */
    private void stubSave() {
        when(movementsRepository.save(any(Movements.class))).thenAnswer(inv -> {
            Movements m = inv.getArgument(0);
            m.setId(100L);
            m.setCreatedAt(LocalDateTime.of(2026, 10, 30, 10, 5));
            return m;
        });
    }

    private Movements savedMovement() {
        ArgumentCaptor<Movements> captor = ArgumentCaptor.forClass(Movements.class);
        verify(movementsRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    // ---------- register ----------

    @Test
    void register_entrada_somaSaldo_gravaUmaMovimentacao_eRetornaStockAfter() {
        Product p = product(25);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(p));
        stubSave();

        MovementResponse response = service
                .register(new CreateMovementRequest(1L, MovementTypeEnum.ENTRY, 5, "Compra"));

        assertThat(p.getQuantity()).isEqualTo(30);
        Movements m = savedMovement();
        assertThat(m.getType()).isEqualTo(MovementTypeEnum.ENTRY);
        assertThat(m.getQuantity()).isEqualTo(5);
        assertThat(m.getProduct()).isSameAs(p);
        assertThat(m.getDescription()).isEqualTo("Compra");

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.product_id()).isEqualTo(1L);
        assertThat(response.product_sku()).isEqualTo("MOUSE-001");
        assertThat(response.product_name()).isEqualTo("Mouse Gamer");
        assertThat(response.product_deleted()).isFalse();
        assertThat(response.type()).isEqualTo(MovementTypeEnum.ENTRY);
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.stock_after()).isEqualTo(30);
        assertThat(response.created_at()).isEqualTo(LocalDateTime.of(2026, 10, 30, 10, 5));
    }

    @Test
    void register_saidaValida_subtraiSaldo() {
        Product p = product(25);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(p));
        stubSave();

        MovementResponse response = service.register(new CreateMovementRequest(1L, MovementTypeEnum.EXIT, 5, null));

        assertThat(p.getQuantity()).isEqualTo(20);
        assertThat(savedMovement().getType()).isEqualTo(MovementTypeEnum.EXIT);
        assertThat(response.stock_after()).isEqualTo(20);
    }

    @Test
    void register_saidaIgualAoSaldo_ePermitida_saldoZero() {
        Product p = product(7);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(p));
        stubSave();

        MovementResponse response = service.register(new CreateMovementRequest(1L, MovementTypeEnum.EXIT, 7, null));

        assertThat(p.getQuantity()).isZero();
        assertThat(response.stock_after()).isZero();
        savedMovement();
    }

    @Test
    void register_saidaMaiorQueSaldo_lancaInsufficientStock_semAlterarSaldoNemGravar() {
        Product p = product(5);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.register(new CreateMovementRequest(1L, MovementTypeEnum.EXIT, 6, null)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("5")
                .hasMessageContaining("6")
                .hasMessageContaining("Mouse Gamer");

        assertThat(p.getQuantity()).isEqualTo(5);
        verify(movementsRepository, never()).save(any());
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void register_produtoInexistenteOuExcluido_lancaNotFound_semGravar() {
        // findActiveByIdForUpdate só enxerga ativos: excluído e inexistente → Optional.empty()
        when(productRepository.findActiveByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(new CreateMovementRequest(9L, MovementTypeEnum.ENTRY, 1, null)))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(movementsRepository);
    }

    @Test
    void register_carregaProdutoComLockPessimista_nuncaPeloFindByIdSimples() {
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(product(1)));
        stubSave();

        service.register(new CreateMovementRequest(1L, MovementTypeEnum.ENTRY, 1, null));

        verify(productRepository).findActiveByIdForUpdate(1L);
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void register_descricaoPadrao_quandoAusenteOuEmBranco() {
        Product p = product(10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(p));
        stubSave();

        MovementResponse entradaNula = service.register(new CreateMovementRequest(1L, MovementTypeEnum.ENTRY, 1, null));
        MovementResponse entradaBranca = service.register(new CreateMovementRequest(1L, MovementTypeEnum.ENTRY, 1, "   "));
        MovementResponse saidaNula = service.register(new CreateMovementRequest(1L, MovementTypeEnum.EXIT, 1, null));
        MovementResponse saidaVazia = service.register(new CreateMovementRequest(1L, MovementTypeEnum.EXIT, 1, ""));

        assertThat(entradaNula.description()).isEqualTo("Entrada de estoque");
        assertThat(entradaBranca.description()).isEqualTo("Entrada de estoque");
        assertThat(saidaNula.description()).isEqualTo("Saída de estoque");
        assertThat(saidaVazia.description()).isEqualTo("Saída de estoque");
    }

    @Test
    void register_stockAfter_acumulaEmOperacoesSucessivas() {
        Product p = product(10);
        when(productRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(p));
        stubSave();

        MovementResponse first = service.register(new CreateMovementRequest(1L, MovementTypeEnum.ENTRY, 10, null));
        MovementResponse second = service.register(new CreateMovementRequest(1L, MovementTypeEnum.EXIT, 15, null));

        assertThat(first.stock_after()).isEqualTo(20);
        assertThat(second.stock_after()).isEqualTo(5);
    }

    // ---------- apply (guarda defensiva) ----------

    @Test
    void apply_quantidadeZeroOuNegativaOuNula_eRejeitada_semAlterarSaldo() {
        Product p = product(10);

        assertThatThrownBy(() -> service.apply(p, MovementTypeEnum.ENTRY, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(p, MovementTypeEnum.EXIT, -3, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(p, MovementTypeEnum.ENTRY, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(p.getQuantity()).isEqualTo(10);
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void apply_tipoOuProdutoNulos_saoRejeitados() {
        assertThatThrownBy(() -> service.apply(product(1), null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(null, MovementTypeEnum.ENTRY, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void apply_estouroDeInteiro_naoCorrompeSaldo() {
        Product p = product(Integer.MAX_VALUE);

        assertThatThrownBy(() -> service.apply(p, MovementTypeEnum.ENTRY, 1, null))
                .isInstanceOf(ArithmeticException.class);

        assertThat(p.getQuantity()).isEqualTo(Integer.MAX_VALUE);
        verifyNoInteractions(movementsRepository);
    }

    @Test
    void apply_gravaMovimentacaoComDescricaoInformada() {
        Product p = product(10);
        stubSave();

        Movements m = service.apply(p, MovementTypeEnum.ENTRY, 2, "Atualizado estoque");

        assertThat(m.getDescription()).isEqualTo("Atualizado estoque");
        assertThat(p.getQuantity()).isEqualTo(12);
    }

    // ---------- search ----------

    @Test
    @SuppressWarnings("unchecked")
    void search_semProductId_naoVerificaExistencia_eMapeiaComStockAfterNulo() {
        Product deleted = product(3);
        deleted.setDeletedAt(LocalDateTime.of(2026, 10, 29, 9, 0));
        Movements m = new Movements();
        m.setId(7L);
        m.setProduct(deleted);
        m.setType(MovementTypeEnum.EXIT);
        m.setQuantity(2);
        m.setDescription("Saída de estoque");
        m.setCreatedAt(LocalDateTime.of(2026, 10, 30, 10, 0));
        when(movementsRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(m));

        List<MovementResponse> result = service.search(new MovementSearchFilter(null, null, null, null));

        verify(productRepository, never()).existsById(any());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).product_deleted()).isTrue();
        assertThat(result.get(0).stock_after()).isNull();

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(movementsRepository).findAll(any(Specification.class), sort.capture());
        assertThat(sort.getValue().toString()).isEqualTo("createdAt: DESC,id: DESC");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_comProductIdExistente_ativoOuExcluido_retornaHistorico() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(movementsRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(service.search(new MovementSearchFilter(1L, null, null, null))).isEmpty();

        verify(productRepository).existsById(1L);
    }

    @Test
    void search_comProductIdInexistente_lancaNotFound() {
        when(productRepository.existsById(eq(5L))).thenReturn(false);

        assertThatThrownBy(() -> service.search(new MovementSearchFilter(5L, null, null, null)))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(movementsRepository);
    }
}
