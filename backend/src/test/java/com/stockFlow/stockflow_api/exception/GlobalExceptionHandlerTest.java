package com.stockFlow.stockflow_api.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockFlow.stockflow_api.config.SecurityConfig;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.exception.SameProductException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Verifica o handler global com um controller de teste (fora do pacote
 * "products", provando que o escopo basePackages foi removido).
 */
@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import({ GlobalExceptionHandlerTest.ThrowingController.class, SecurityConfig.class })
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/product/_test")
    static class ThrowingController {

        record Body(@NotBlank(message = "obrigatório") String name) {
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new ProductNotFoundException(42L);
        }

        @GetMapping("/same")
        String same() {
            throw new SameProductException("N", "S");
        }

        @GetMapping("/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("could not execute statement; Duplicate entry 'x' for key 'uk'");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("segredo interno: jdbc:mysql://host/db senha=123");
        }

        @GetMapping("/typed")
        String typed(@RequestParam Integer n) {
            return "ok" + n;
        }

        @PostMapping("/validated")
        String validated(@Valid @RequestBody Body body) {
            return "ok";
        }
    }

    @Test
    void productNotFound_retorna404() throws Exception {
        mockMvc.perform(get("/product/_test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("produto não encontrado"))
                .andExpect(jsonPath("$.message").value("Produto não encontrado: 42"))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
    }

    @Test
    void sameProduct_retorna409() throws Exception {
        mockMvc.perform(get("/product/_test/same"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Produto já cadastrado com nome ou sku"));
    }

    @Test
    void dataIntegrityViolation_retorna409_semVazarDetalhesDoBanco() throws Exception {
        mockMvc.perform(get("/product/_test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Violação de integridade"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Duplicate entry"))));
    }

    @Test
    void validacao_retorna400ComFieldErrors() throws Exception {
        mockMvc.perform(post("/product/_test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validação falhou"))
                .andExpect(jsonPath("$.field_errors.name").value("obrigatório"));
    }

    @Test
    void jsonIlegivel_retorna400() throws Exception {
        mockMvc.perform(post("/product/_test/validated").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"));
    }

    @Test
    void corpoAusente_retorna400() throws Exception {
        mockMvc.perform(post("/product/_test/validated").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tipoDeParametroInvalido_retorna400() throws Exception {
        mockMvc.perform(get("/product/_test/typed").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"))
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'n'"));
    }

    @Test
    void excecaoInesperada_retorna500Generico_semVazarDetalhes() throws Exception {
        mockMvc.perform(get("/product/_test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Erro interno"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo"))))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
    }

    @Test
    void parametroObrigatorioAusente_preservaStatus400_naoViraNovo500() throws Exception {
        mockMvc.perform(get("/product/_test/typed"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metodoNaoSuportado_preservaStatus405_naoViraNovo500() throws Exception {
        mockMvc.perform(post("/product/_test/not-found"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rotaInexistente_preservaStatus404_naoViraNovo500() throws Exception {
        mockMvc.perform(get("/product/_test/nao-existe"))
                .andExpect(status().isNotFound());
    }
}
