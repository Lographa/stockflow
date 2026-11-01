package com.stockFlow.stockflow_api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockFlow.stockflow_api.products.ProductController;
import com.stockFlow.stockflow_api.products.ProductService;

/** stockflow.cors.allowed-origins é configurável (lista separada por vírgula). */
@WebMvcTest(value = ProductController.class, properties = "stockflow.cors.allowed-origins=https://app.exemplo.com,http://localhost:5173")
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityCorsPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void origensConfiguradas_saoAceitas() throws Exception {
        for (String origin : new String[] { "https://app.exemplo.com", "http://localhost:5173" }) {
            mockMvc.perform(options("/product")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
    }

    @Test
    void origemPadrao_deixaDeSerAceita_quandoPropriedadeSobrescreve() throws Exception {
        mockMvc.perform(options("/product")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
