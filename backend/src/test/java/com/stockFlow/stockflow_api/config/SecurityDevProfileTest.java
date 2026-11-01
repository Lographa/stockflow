package com.stockFlow.stockflow_api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockFlow.stockflow_api.products.ProductController;
import com.stockFlow.stockflow_api.products.ProductService;

/** Perfil dev: API aberta + CORS para http://localhost:3000. */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class SecurityDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getProduct_naoRetorna401() throws Exception {
        when(productService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/product")).andExpect(status().isOk());
    }

    @Test
    void postCreate_semCsrf_naoRetorna401nem403() throws Exception {
        // corpo inválido de propósito: chega no controller (400), provando que a segurança liberou
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movements_estaLiberado_naoRetorna401() throws Exception {
        // ainda não existe controller de /movements (BE-2): 404 prova que passou pela segurança
        int status = mockMvc.perform(get("/movements")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }

    @Test
    void outrasRotas_continuamExigindoAutenticacao_401() throws Exception {
        mockMvc.perform(get("/qualquer-outra-rota")).andExpect(status().isUnauthorized());
    }

    @Test
    void preflightCors_deLocalhost3000_eAceito() throws Exception {
        mockMvc.perform(options("/product")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("DELETE")));
    }

    @Test
    void preflightCors_deDeleteEmProductId_eAceito() throws Exception {
        mockMvc.perform(options("/product/1")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void preflightCors_deOutraOrigem_eNegado() throws Exception {
        mockMvc.perform(options("/product")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requisicaoSimples_deLocalhost3000_recebeCabecalhoCors() throws Exception {
        when(productService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/product").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }
}
