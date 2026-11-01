package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * QA-03 (opcional) — segurança por perfil em CONTEXTO REAL (D-9): sem os perfis
 * {@code dev}/{@code test} a API é fechada (401). Usa o perfil {@code prod}, que
 * cai no filtro "fechado" ({@code !dev & !test}) e não herda o perfil {@code test}
 * da base; assim o resultado não depende de {@code SPRING_PROFILES_ACTIVE} do ambiente.
 */
@Tag("integration")
@ActiveProfiles(profiles = "prod", inheritProfiles = false)
class SecurityClosedProfileIntegrationTest extends AbstractMySqlIntegrationTest {

    @Test
    void semPerfilAberto_getProduct_retorna401() throws Exception {
        mockMvc.perform(get("/product")).andExpect(status().isUnauthorized());
    }

    @Test
    void semPerfilAberto_movimentacoesEEscritas_retornam401_enadaEGravado() throws Exception {
        mockMvc.perform(get("/movements")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content(movementJson(1, "ENTRY", 1))).andExpect(status().isUnauthorized());
        String sku = newSku();
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, newName(), 1, 0))).andExpect(status().isUnauthorized());
        assertThat(productRows(sku)).isZero();
    }
}
