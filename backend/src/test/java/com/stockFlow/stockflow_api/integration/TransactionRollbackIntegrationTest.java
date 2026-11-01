package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.stockFlow.stockflow_api.movements.MovementsRepository;
import com.stockFlow.stockflow_api.movements.StockMovementService;
import com.stockFlow.stockflow_api.movements.dto.CreateMovementRequest;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.products.ProductService;
import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.UpdateProductRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * QA-03 — transação/rollback (AGENTS.md: saldo e movimentação na MESMA
 * transação). {@code MovementsRepository.save} é espionado para gravar a
 * movimentação, dar {@code flush} (o UPDATE do saldo também vai ao MySQL, dentro
 * da transação) e SÓ ENTÃO lançar exceção. O teste prova duas coisas:
 * (1) dentro da transação o saldo já estava alterado no MySQL;
 * (2) depois do rollback, saldo e histórico voltaram ao estado original — nada órfão.
 *
 * Fica em classe própria porque o spy cria um contexto Spring separado.
 */
@Tag("integration")
class TransactionRollbackIntegrationTest extends AbstractMySqlIntegrationTest {

    @MockitoSpyBean
    private MovementsRepository movementsRepository;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private ProductService productService;

    @PersistenceContext
    private EntityManager entityManager;

    /** Valor lido DENTRO da transação (mesma conexão), após o flush e antes da exceção. */
    private final AtomicReference<Object> seenInsideTransaction = new AtomicReference<>();

    /**
     * Arma a falha: movimentação gravada + flush + leitura nativa na mesma transação + exceção.
     *
     * @param observeSql consulta nativa de 1 parâmetro executada dentro da transação
     */
    private void failAfterSavingMovement(String observeSql, Object parameter) {
        doAnswer(invocation -> {
            invocation.callRealMethod(); // INSERT da movimentação
            entityManager.flush(); // envia também o UPDATE do saldo (e demais alterações pendentes)
            seenInsideTransaction.set(entityManager.createNativeQuery(observeSql)
                    .setParameter(1, parameter).getSingleResult());
            throw new IllegalStateException("falha simulada após alterar o saldo");
        }).when(movementsRepository).save(any());
    }

    // ------------------------------------------------------------------ movimentação (saída/entrada)

    @Test
    void saidaQueFalhaAposAlterarOSaldo_revertSaldoEHistorico() throws Exception {
        long id = createProduct(10, 0);
        failAfterSavingMovement("SELECT quantity FROM products WHERE id = ?1", id);

        assertThatThrownBy(() -> stockMovementService
                .register(new CreateMovementRequest(id, MovementTypeEnum.EXIT, 3, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha simulada");

        verify(movementsRepository).save(any());
        // dentro da transação o saldo JÁ estava em 7 no MySQL ...
        assertThat(((Number) seenInsideTransaction.get()).intValue()).isEqualTo(7);
        // ... e depois do rollback voltou a 10, sem movimentação órfã
        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(movementCount(id)).isEqualTo(1);
        assertThat(movementCount(id, "EXIT")).isZero();
        assertBalanceMatchesHistory(id);
    }

    @Test
    void entradaQueFalhaAposAlterarOSaldo_revertSaldoEHistorico() throws Exception {
        long id = createProduct(10, 0);
        failAfterSavingMovement("SELECT quantity FROM products WHERE id = ?1", id);

        assertThatThrownBy(() -> stockMovementService
                .register(new CreateMovementRequest(id, MovementTypeEnum.ENTRY, 5, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(((Number) seenInsideTransaction.get()).intValue()).isEqualTo(15);
        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(movementCount(id)).isEqualTo(1);
        assertBalanceMatchesHistory(id);
    }

    @Test
    void movimentacaoViaHttpQueFalha_retorna500_eSaldoEHistoricoNaoMudam() throws Exception {
        long id = createProduct(10, 0);
        failAfterSavingMovement("SELECT quantity FROM products WHERE id = ?1", id);

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 4)))
                .andExpect(status().isInternalServerError());

        assertThat(((Number) seenInsideTransaction.get()).intValue()).isEqualTo(6);
        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(movementCount(id)).isEqualTo(1);
        assertBalanceMatchesHistory(id);
    }

    // ------------------------------------------------------------------ criação de produto

    @Test
    void criarProdutoComFalhaAoGravarAEntradaInicial_produtoNaoFicaCriado() throws Exception {
        String sku = newSku();
        String name = newName();
        failAfterSavingMovement("SELECT COUNT(*) FROM products WHERE sku = ?1", sku);

        assertThatThrownBy(() -> productService.createProduct(
                new CreateProductRequest(sku, name, "desc", new BigDecimal("10.00"), 5, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha simulada");

        // dentro da transação o produto já existia no MySQL (INSERT executado) ...
        assertThat(((Number) seenInsideTransaction.get()).intValue()).isEqualTo(1);
        // ... e após o rollback NÃO fica produto algum (nem movimentação órfã)
        assertThat(productRows(sku)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE name = ?", Integer.class, name))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movements m LEFT JOIN products p ON p.id = m.product_id WHERE p.id IS NULL",
                Integer.class)).as("movimentações órfãs").isZero();
    }

    @Test
    void criarProdutoViaHttpComFalha_retorna500_enomeESkuContinuamLivres() throws Exception {
        String sku = newSku();
        String name = newName();
        failAfterSavingMovement("SELECT COUNT(*) FROM products WHERE sku = ?1", sku);

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, name, 5, 0)))
                .andExpect(status().isInternalServerError());
        assertThat(productRows(sku)).isZero();

        // desarma a falha: o mesmo SKU/nome agora pode ser criado (nada ficou reservado)
        org.mockito.Mockito.reset(movementsRepository);
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, name, 5, 0)))
                .andExpect(status().isCreated());
        track(jdbc.queryForObject("SELECT id FROM products WHERE sku = ?", Long.class, sku));
        assertThat(productRows(sku)).isEqualTo(1);
    }

    @Test
    void criarProdutoComQuantidadeZero_naoGravaMovimentacao_logoNaoDisparaAFalhaArmada() throws Exception {
        String sku = newSku();
        failAfterSavingMovement("SELECT COUNT(*) FROM products WHERE sku = ?1", sku);

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, newName(), 0, 0)))
                .andExpect(status().isCreated());
        track(jdbc.queryForObject("SELECT id FROM products WHERE sku = ?", Long.class, sku));

        verify(movementsRepository, never()).save(any());
        assertThat(productRows(sku)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ ajuste via PUT

    @Test
    void putComAjusteDeQuantidadeQueFalha_naoAlteraNadaDoProduto_nemOHistorico() throws Exception {
        String sku = newSku();
        String name = newName();
        long id = createProduct(sku, name, 10, 0);
        failAfterSavingMovement("SELECT quantity FROM products WHERE id = ?1", id);

        assertThatThrownBy(() -> productService.updateProduct(id,
                new UpdateProductRequest(null, "Nome alterado " + unique(), "outra descricao",
                        new BigDecimal("77.70"), 20, 3)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(((Number) seenInsideTransaction.get()).intValue()).isEqualTo(20);
        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, id)).isEqualTo(name);
        assertThat(jdbc.queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("10.50");
        assertThat(jdbc.queryForObject("SELECT min_stock FROM products WHERE id = ?", Integer.class, id)).isZero();
        assertThat(movementCount(id)).isEqualTo(1);
        assertBalanceMatchesHistory(id);
    }

    @Test
    void putViaHttpComAjusteQueFalha_retorna500_eSaldoPermanece() throws Exception {
        long id = createProduct(10, 0);
        failAfterSavingMovement("SELECT quantity FROM products WHERE id = ?1", id);

        mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
                .andExpect(status().isInternalServerError());

        assertThat(((Number) seenInsideTransaction.get()).intValue()).isEqualTo(2);
        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(movementCount(id)).isEqualTo(1);
        assertBalanceMatchesHistory(id);
    }
}
