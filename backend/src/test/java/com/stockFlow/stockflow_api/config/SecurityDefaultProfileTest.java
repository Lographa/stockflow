package com.stockFlow.stockflow_api.config;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import com.stockFlow.stockflow_api.products.ProductController;
import com.stockFlow.stockflow_api.products.ProductService;

import static org.assertj.core.api.Assertions.assertThat;

/** Sem perfil ativo (padrão = produção): fechado, 401 em tudo, sem CORS. */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class SecurityDefaultProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private ProductService productService;

    @Test
    void getProduct_retorna401() throws Exception {
        mockMvc.perform(get("/product")).andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void escritas_retornam401_naoAlcancamController() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/product/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void movements_e_qualquerRota_retornam401() throws Exception {
        mockMvc.perform(get("/movements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/qualquer-outra-rota")).andExpect(status().isUnauthorized());
    }

    @Test
    void preflightCors_deLocalhost3000_eNegado_semCabecalhosCors() throws Exception {
        mockMvc.perform(options("/product")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void requisicaoComOrigin_naoRecebeCabecalhoCors() throws Exception {
        mockMvc.perform(get("/product").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void beanDeCors_naoExisteForaDeDevOuTest() {
		assertThat(context.containsBean("corsConfigurationSource")).isFalse();
    }
}
