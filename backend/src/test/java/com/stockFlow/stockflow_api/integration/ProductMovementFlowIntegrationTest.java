package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.IllegalTransactionStateException;

import com.stockFlow.stockflow_api.movements.StockMovementService;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.products.Product;

/**
 * QA-03 — fluxo completo pela camada HTTP real (MockMvc sobre o contexto
 * completo + MySQL real): criação, entrada, saída, saída inválida (409),
 * histórico ordenado, stock_after, timestamps (P-09), ajuste de PUT (D-1) e
 * consistência saldo × histórico (critério transversal 2).
 */
@Tag("integration")
class ProductMovementFlowIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private StockMovementService stockMovementService;

    @Test
    void apply_foraDeTransacao_eRejeitado_saldoEMovimentacaoNuncaSaoConfirmadosSeparadamente() throws Exception {
        long id = createProduct(10, 0);
        Product detached = new Product();
        detached.setQuantity(10);

        // Propagation.MANDATORY: sem transação em andamento a operação interna nem começa
        assertThatThrownBy(() -> stockMovementService.apply(detached, MovementTypeEnum.EXIT, 1, null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(movementCount(id)).isEqualTo(1);
    }

    @Test
    void criarProdutoComQuantidade_retorna201_comTimestamps_eGeraUmaEntry() throws Exception {
        String sku = newSku();
        String name = newName();

        String body = mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, name, 10, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.min_stock").value(10))
                .andExpect(jsonPath("$.low_stock").value(false))
                // P-09 / @Generated: created_at e updated_at já saem preenchidos pelo MySQL
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
        track(id);

        assertThat(movementCount(id)).isEqualTo(1);
        Map<String, Object> movement = jdbc.queryForMap(
                "SELECT type, quantity, description, created_at FROM stock_movements WHERE product_id = ?", id);
        assertThat(movement.get("type")).isEqualTo("ENTRY");
        assertThat(movement.get("quantity")).isEqualTo(10);
        assertThat(movement.get("description")).isEqualTo("Entrada de estoque");
        assertThat(movement.get("created_at")).isNotNull();

        Map<String, Object> row = jdbc.queryForMap("SELECT created_at, updated_at, deleted_at FROM products WHERE id = ?",
                id);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNull();
        assertBalanceMatchesHistory(id);
    }

    @Test
    void criarProdutoComQuantidadeZero_retorna201_semNenhumaMovimentacao() throws Exception {
        long id = createProduct(0, 5);

        assertThat(quantityOf(id)).isZero();
        assertThat(movementCount(id)).isZero();
        mockMvc.perform(get("/movements").param("product_id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        assertBalanceMatchesHistory(id);
    }

    @Test
    void fluxoCompleto_entrada_saida_saidaInvalida_historicoOrdenado_eStockAfter() throws Exception {
        long id = createProduct(10, 10);

        // entrada
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "ENTRY", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.product_id").value(id))
                .andExpect(jsonPath("$.type").value("ENTRY"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.stock_after").value(15))
                .andExpect(jsonPath("$.product_deleted").value(false))
                .andExpect(jsonPath("$.description").value("Entrada de estoque"))
                .andExpect(jsonPath("$.created_at", notNullValue()));
        assertThat(quantityOf(id)).isEqualTo(15);

        // saída válida
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXIT"))
                .andExpect(jsonPath("$.stock_after").value(8))
                .andExpect(jsonPath("$.description").value("Saída de estoque"))
                .andExpect(jsonPath("$.created_at", notNullValue()));
        assertThat(quantityOf(id)).isEqualTo(8);
        assertThat(movementCount(id)).isEqualTo(3);

        // saída maior que o saldo → 409, sem alterar saldo nem histórico
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 9)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Estoque insuficiente"));
        assertThat(quantityOf(id)).isEqualTo(8);
        assertThat(movementCount(id)).isEqualTo(3);

        // histórico: created_at desc, id desc; stock_after nulo no GET; created_at preenchido
        mockMvc.perform(get("/movements").param("product_id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("EXIT"))
                .andExpect(jsonPath("$[0].quantity").value(7))
                .andExpect(jsonPath("$[1].type").value("ENTRY"))
                .andExpect(jsonPath("$[1].quantity").value(5))
                .andExpect(jsonPath("$[2].type").value("ENTRY"))
                .andExpect(jsonPath("$[2].quantity").value(10))
                .andExpect(jsonPath("$[*].stock_after", org.hamcrest.Matchers.everyItem(nullValue())))
                .andExpect(jsonPath("$[*].created_at", org.hamcrest.Matchers.everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].product_deleted", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))));

        // detalhe: saldo e low_stock calculado no backend (8 < 10)
        mockMvc.perform(get("/product/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.low_stock").value(true));

        assertBalanceMatchesHistory(id);
    }

    @Test
    void saidaMaiorQueSaldo_retorna409_semAlterarSaldoNemHistorico() throws Exception {
        long id = createProduct(3, 0);

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 4)))
                .andExpect(status().isConflict());

        assertThat(quantityOf(id)).isEqualTo(3);
        assertThat(movementCount(id)).isEqualTo(1);
        assertThat(movementCount(id, "EXIT")).isZero();
        assertBalanceMatchesHistory(id);
    }

    @Test
    void saidaDoSaldoTotal_deixaEstoqueZeradoENuncaNegativo() throws Exception {
        long id = createProduct(5, 0);

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock_after").value(0));
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 1)))
                .andExpect(status().isConflict());

        assertThat(quantityOf(id)).isZero();
        assertBalanceMatchesHistory(id);
    }

    @Test
    void movimentacaoInvalida_retorna400_enadaEGravado() throws Exception {
        long id = createProduct(5, 0);

        for (String body : List.of(movementJson(id, "ENTRY", 0), movementJson(id, "EXIT", -3),
                movementJson(id, "OUTRO", 1))) {
            mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        assertThat(quantityOf(id)).isEqualTo(5);
        assertThat(movementCount(id)).isEqualTo(1);
    }

    @Test
    void movimentacaoParaProdutoInexistente_retorna404() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(987654321L, "ENTRY", 1)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/movements").param("product_id", "987654321"))
                .andExpect(status().isNotFound());
    }

    @Test
    void historico_filtraPorTipo() throws Exception {
        long id = createProduct(10, 0);
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 2))).andExpect(status().isCreated());

        mockMvc.perform(get("/movements").param("product_id", String.valueOf(id)).param("type", "EXIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("EXIT"));
        mockMvc.perform(get("/movements").param("product_id", String.valueOf(id)).param("type", "ENTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("ENTRY"));
    }

    @Test
    void putComQuantity_geraMovimentacaoDeAjuste_D1_eIgualNaoGeraNada() throws Exception {
        long id = createProduct(10, 0);

        // aumento 10 → 15: ENTRY 5 "Atualizado estoque"
        mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
        assertThat(movementCount(id)).isEqualTo(2);
        Map<String, Object> adjust = jdbc.queryForMap(
                "SELECT type, quantity, description FROM stock_movements WHERE product_id = ? ORDER BY id DESC LIMIT 1",
                id);
        assertThat(adjust.get("type")).isEqualTo("ENTRY");
        assertThat(adjust.get("quantity")).isEqualTo(5);
        assertThat(adjust.get("description")).isEqualTo("Atualizado estoque");

        // redução 15 → 4: EXIT 11
        mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4));
        assertThat(movementCount(id)).isEqualTo(3);
        assertThat(sumOf(id, "EXIT")).isEqualTo(11);

        // mesma quantidade e/ou só outros campos → nenhuma movimentação
        mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":4}")).andExpect(status().isOk());
        mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":99.90,\"description\":\"novo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(99.90));
        assertThat(movementCount(id)).isEqualTo(3);

        assertBalanceMatchesHistory(id);
    }

    @Test
    void consistencia_sequenciaMistaDeOperacoes_saldoSempreIgualAoHistoricoENuncaNegativo() throws Exception {
        long id = createProduct(20, 5);
        Random random = new Random(42); // semente fixa: execução reprodutível
        int expected = 20;

        for (int i = 0; i < 60; i++) {
            int op = random.nextInt(3);
            int qty = 1 + random.nextInt(15);
            if (op == 0) { // ENTRY
                mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                        .content(movementJson(id, "ENTRY", qty))).andExpect(status().isCreated());
                expected += qty;
            } else if (op == 1) { // EXIT (pode ser recusada)
                var result = mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                        .content(movementJson(id, "EXIT", qty))).andReturn();
                if (qty <= expected) {
                    assertThat(result.getResponse().getStatus()).isEqualTo(201);
                    expected -= qty;
                } else {
                    assertThat(result.getResponse().getStatus()).isEqualTo(409);
                }
            } else { // PUT absoluto (ajuste)
                int target = random.nextInt(40);
                mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + target + "}")).andExpect(status().isOk());
                expected = target;
            }
            assertThat(quantityOf(id)).as("passo %d", i).isEqualTo(expected);
            assertBalanceMatchesHistory(id);
        }
    }

    @Test
    void skuOuNomeDuplicado_retorna409_enaoCriaSegundoProduto() throws Exception {
        String sku = newSku();
        String name = newName();
        long id = createProduct(sku, name, 1, 0);

        // mesmo SKU, nome diferente
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, newName(), 1, 0)))
                .andExpect(status().isConflict());
        // mesmo nome (case-insensitive, collation utf8mb4_unicode_ci), SKU diferente
        String otherSku = newSku();
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(otherSku, name.toUpperCase(), 1, 0)))
                .andExpect(status().isConflict());

        assertThat(productRows(otherSku)).isZero();
        assertThat(movementCount(id)).isEqualTo(1);
    }

    @Test
    void validacaoDePreco_precoZeroAceito_precoNegativoRetorna400_semGravar() throws Exception {
        String zeroSku = newSku();
        String body = mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"%s\",\"name\":\"%s\",\"price\":0,\"quantity\":0,\"min_stock\":0}"
                        .formatted(zeroSku, newName())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        track(((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue());

        String negSku = newSku();
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"%s\",\"name\":\"%s\",\"price\":-1,\"quantity\":0,\"min_stock\":0}"
                        .formatted(negSku, newName())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.price", notNullValue()));
        assertThat(productRows(negSku)).isZero();
    }

    @Test
    void consultaDeProdutos_incluiProdutoAtivo_eBuscaPorQ() throws Exception {
        String name = newName();
        String sku = newSku();
        long id = createProduct(sku, name, 2, 10);

        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku=='" + sku + "')]", hasSize(1)));
        mockMvc.perform(get("/product/search").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].low_stock").value(true));
        mockMvc.perform(get("/product/search").param("q", name).param("lowStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/product/search").param("q", "IT-NAO-EXISTE-" + unique()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
