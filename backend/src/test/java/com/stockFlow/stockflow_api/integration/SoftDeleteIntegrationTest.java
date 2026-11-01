package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * QA-03 — soft delete (D-2 / D-6 / T-05) contra MySQL real: produto excluído
 * some das consultas e recusa movimentações, mas as linhas, o saldo e o
 * histórico permanecem; nome e SKU continuam reservados.
 */
@Tag("integration")
class SoftDeleteIntegrationTest extends AbstractMySqlIntegrationTest {

    @Test
    void delete_retorna204_produtoSomeDasConsultas_eNadaEFisicamenteRemovido() throws Exception {
        String sku = newSku();
        String name = newName();
        long id = createProduct(sku, name, 10, 0);
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 4))).andExpect(status().isCreated());

        int movementsBefore = movementCount(id);
        int balanceBefore = quantityOf(id);
        assertThat(movementsBefore).isEqualTo(2);

        mockMvc.perform(delete("/product/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // invisível em /{id}, /product, /search
        mockMvc.perform(get("/product/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku=='" + sku + "')]", hasSize(0)));
        mockMvc.perform(get("/product/search").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/product/search").param("ids", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // linha preservada: deleted_at preenchido, saldo e movimentações intactos (nenhuma movimentação criada)
        Map<String, Object> row = jdbc.queryForMap("SELECT deleted_at, quantity, sku, name FROM products WHERE id = ?",
                id);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("quantity")).isEqualTo(balanceBefore);
        assertThat(row.get("sku")).isEqualTo(sku);
        assertThat(movementCount(id)).isEqualTo(movementsBefore);
        assertBalanceMatchesHistory(id);

        // excluir de novo / alterar produto excluído → 404
        mockMvc.perform(delete("/product/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(put("/product/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":99}")).andExpect(status().isNotFound());
        assertThat(quantityOf(id)).isEqualTo(balanceBefore);
        assertThat(movementCount(id)).isEqualTo(movementsBefore);
    }

    @Test
    void movimentacaoParaProdutoExcluido_retorna404_enadaEGravado() throws Exception {
        long id = createProduct(10, 0);
        mockMvc.perform(delete("/product/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "ENTRY", 1))).andExpect(status().isNotFound());
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 1))).andExpect(status().isNotFound());

        assertThat(quantityOf(id)).isEqualTo(10);
        assertThat(movementCount(id)).isEqualTo(1);
    }

    @Test
    void historicoDeProdutoExcluido_continuaAcessivel_comProductDeletedTrue() throws Exception {
        String sku = newSku();
        long id = createProduct(sku, newName(), 10, 0);
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(id, "EXIT", 3))).andExpect(status().isCreated());
        mockMvc.perform(delete("/product/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/movements").param("product_id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("EXIT"))
                .andExpect(jsonPath("$[1].type").value("ENTRY"))
                .andExpect(jsonPath("$[*].product_deleted", everyItem(is(true))))
                .andExpect(jsonPath("$[*].product_sku", everyItem(is(sku))));

        // sem product_id: movimentações de todos os produtos, inclusive excluídos
        mockMvc.perform(get("/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.product_sku=='" + sku + "')]", hasSize(2)));
    }

    @Test
    void recriarComMesmoNome_ouMesmoSku_deProdutoExcluido_retorna409() throws Exception {
        String sku = newSku();
        String name = newName();
        long id = createProduct(sku, name, 1, 0);
        mockMvc.perform(delete("/product/" + id)).andExpect(status().isNoContent());

        // mesmo nome (SKU novo) → 409
        String newSkuValue = newSku();
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(newSkuValue, name, 1, 0)))
                .andExpect(status().isConflict());
        // mesmo SKU (nome novo) → 409
        String newNameValue = newName();
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, newNameValue, 1, 0)))
                .andExpect(status().isConflict());

        assertThat(productRows(newSkuValue)).isZero();
        assertThat(productRows(sku)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE name = ?", Integer.class, newNameValue))
                .isZero();
    }

    @Test
    void putComNomeOuSkuDeProdutoExcluido_retorna409() throws Exception {
        String deletedSku = newSku();
        String deletedName = newName();
        long deletedId = createProduct(deletedSku, deletedName, 1, 0);
        mockMvc.perform(delete("/product/" + deletedId)).andExpect(status().isNoContent());

        long activeId = createProduct(1, 0);
        mockMvc.perform(put("/product/" + activeId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + deletedName + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/product/" + activeId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + deletedSku + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_deProdutoInexistente_retorna404() throws Exception {
        mockMvc.perform(delete("/product/987654321")).andExpect(status().isNotFound());
    }
}
