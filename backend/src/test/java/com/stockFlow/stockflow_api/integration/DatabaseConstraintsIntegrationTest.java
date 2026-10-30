package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.stockFlow.stockflow_api.integration.support.SqlErrors;

/**
 * QA-03 — constraints do schema (init.sql) violadas por SQL direto: o banco
 * rejeita independentemente da aplicação (última linha de defesa).
 * Códigos MySQL: 3819 = CHECK violado, 1452 = FK, 1062 = UNIQUE duplicado.
 */
@Tag("integration")
class DatabaseConstraintsIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final int ER_CHECK_CONSTRAINT_VIOLATED = 3819;
    private static final int ER_NO_REFERENCED_ROW = 1452;
    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_ROW_IS_REFERENCED = 1451;

    private static final String INSERT_PRODUCT = "INSERT INTO products (sku, name, price, quantity, min_stock) VALUES (?,?,?,?,?)";

    private long insertProduct(String sku, String name) {
        jdbc.update(INSERT_PRODUCT, sku, name, 10, 0, 0);
        long id = jdbc.queryForObject("SELECT id FROM products WHERE sku = ?", Long.class, sku);
        track(id);
        return id;
    }

    private void assertRejected(int expectedErrorCode, Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isEqualTo(expectedErrorCode));
    }

    // ------------------------------------------------------------------ CHECK

    @Test
    void check_movimentacaoComQuantidadeZeroOuNegativa_eRejeitada() {
        long id = insertProduct(newSku(), newName());

        for (int quantity : new int[] { 0, -1, -100 }) {
            assertRejected(ER_CHECK_CONSTRAINT_VIOLATED, () -> jdbc.update(
                    "INSERT INTO stock_movements (product_id, type, quantity) VALUES (?, 'ENTRY', ?)", id, quantity));
        }
        assertThat(movementCount(id)).isZero();
    }

    @Test
    void check_movimentacaoComTipoInvalido_eRejeitada() {
        long id = insertProduct(newSku(), newName());

        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED, () -> jdbc.update(
                "INSERT INTO stock_movements (product_id, type, quantity) VALUES (?, 'OUTRO', 1)", id));
        assertThat(movementCount(id)).isZero();
    }

    @Test
    void check_produtoComPrecoNegativo_eRejeitado() {
        String sku = newSku();
        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED, () -> jdbc.update(INSERT_PRODUCT, sku, newName(), -0.01, 0, 0));
        assertThat(productRows(sku)).isZero();
    }

    @Test
    void check_produtoComQuantidadeOuEstoqueMinimoNegativo_eRejeitado() {
        String skuQuantity = newSku();
        String skuMin = newSku();
        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED, () -> jdbc.update(INSERT_PRODUCT, skuQuantity, newName(), 1, -1, 0));
        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED, () -> jdbc.update(INSERT_PRODUCT, skuMin, newName(), 1, 0, -1));
        assertThat(productRows(skuQuantity)).isZero();
        assertThat(productRows(skuMin)).isZero();
    }

    @Test
    void check_updateQueDeixariaSaldoPrecoOuMinimoNegativo_eRejeitado_eLinhaPermanece() {
        long id = insertProduct(newSku(), newName());

        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED,
                () -> jdbc.update("UPDATE products SET quantity = -1 WHERE id = ?", id));
        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED,
                () -> jdbc.update("UPDATE products SET price = -5 WHERE id = ?", id));
        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED,
                () -> jdbc.update("UPDATE products SET min_stock = -5 WHERE id = ?", id));
        assertThat(quantityOf(id)).isZero();
    }

    @Test
    void check_valoresLimite_zeroEPermitidoParaPrecoQuantidadeEMinimo() {
        String sku = newSku();
        jdbc.update(INSERT_PRODUCT, sku, newName(), 0, 0, 0);
        track(jdbc.queryForObject("SELECT id FROM products WHERE sku = ?", Long.class, sku));
        assertThat(productRows(sku)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ FK

    @Test
    void fk_movimentacaoParaProdutoInexistente_eRejeitada() {
        assertRejected(ER_NO_REFERENCED_ROW, () -> jdbc.update(
                "INSERT INTO stock_movements (product_id, type, quantity) VALUES (987654321, 'ENTRY', 1)"));
    }

    @Test
    void fk_naoPermiteApagarProdutoQueTemMovimentacoes() {
        long id = insertProduct(newSku(), newName());
        jdbc.update("INSERT INTO stock_movements (product_id, type, quantity) VALUES (?, 'ENTRY', 1)", id);

        assertRejected(ER_ROW_IS_REFERENCED, () -> jdbc.update("DELETE FROM products WHERE id = ?", id));
        assertThat(productRows(jdbc.queryForObject("SELECT sku FROM products WHERE id = ?", String.class, id)))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------ UNIQUE

    @Test
    void unique_skuDuplicado_eRejeitado() {
        String sku = newSku();
        insertProduct(sku, newName());

        assertRejected(ER_DUP_ENTRY, () -> jdbc.update(INSERT_PRODUCT, sku, newName(), 1, 0, 0));
        assertThat(productRows(sku)).isEqualTo(1);
    }

    @Test
    void unique_nomeDuplicado_uk_products_name_eRejeitado_inclusiveIgnorandoCaixa() {
        String name = newName();
        insertProduct(newSku(), name);

        assertThatThrownBy(() -> jdbc.update(INSERT_PRODUCT, newSku(), name, 1, 0, 0))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_products_name")
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isEqualTo(ER_DUP_ENTRY));
        // collation utf8mb4_unicode_ci: sem diferença de maiúsculas/minúsculas
        assertRejected(ER_DUP_ENTRY, () -> jdbc.update(INSERT_PRODUCT, newSku(), name.toLowerCase(), 1, 0, 0));
    }

    @Test
    void unique_nomeESkuDeProdutoExcluidoLogicamente_continuamReservados() throws Exception {
        String sku = newSku();
        String name = newName();
        long id = insertProduct(sku, name);
        // exclusão lógica pela própria API (SQL direto não é necessário): garante deleted_at preenchido
        mockMvc.perform(delete("/product/" + id)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM products WHERE id = ?", Boolean.class, id))
                .isTrue();

        assertRejected(ER_DUP_ENTRY, () -> jdbc.update(INSERT_PRODUCT, newSku(), name, 1, 0, 0));
        assertRejected(ER_DUP_ENTRY, () -> jdbc.update(INSERT_PRODUCT, sku, newName(), 1, 0, 0));
    }

    // ------------------------------------------------------------------ defaults do schema

    @Test
    void defaults_timestampsEDeletedAtSaoPreenchidosPeloBanco() {
        long id = insertProduct(newSku(), newName());
        jdbc.update("INSERT INTO stock_movements (product_id, type, quantity) VALUES (?, 'ENTRY', 1)", id);

        assertThat(jdbc.queryForObject(
                "SELECT created_at IS NOT NULL AND updated_at IS NOT NULL AND deleted_at IS NULL FROM products WHERE id = ?",
                Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_at IS NOT NULL FROM stock_movements WHERE product_id = ?",
                Boolean.class, id)).isTrue();
    }
}
