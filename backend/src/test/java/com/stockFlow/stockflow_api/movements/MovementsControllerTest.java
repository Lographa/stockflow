package com.stockFlow.stockflow_api.movements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockFlow.stockflow_api.config.SecurityConfig;
import com.stockFlow.stockflow_api.movements.dto.CreateMovementRequest;
import com.stockFlow.stockflow_api.movements.dto.MovementResponse;
import com.stockFlow.stockflow_api.movements.dto.MovementSearchFilter;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.exception.InsufficientStockException;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;

@WebMvcTest(MovementsController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MovementsControllerTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 10, 30, 10, 5, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockMovementService stockMovementService;

    private static MovementResponse response(Integer stockAfter, boolean deleted) {
        return new MovementResponse(10L, 1L, "MOUSE-001", "Mouse Gamer", deleted, MovementTypeEnum.EXIT, 5,
                "Separacao de pedidos", stockAfter, TS);
    }

    private static final String VALID = """
            {"product_id":1,"type":"EXIT","quantity":5,"description":"Separacao de pedidos"}
            """;

    // ---------- POST /movements ----------

    @Test
    void post_valido_retorna201_comContratoCompleto() throws Exception {
        when(stockMovementService.register(any(CreateMovementRequest.class))).thenReturn(response(20, false));

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.product_id").value(1))
                .andExpect(jsonPath("$.product_sku").value("MOUSE-001"))
                .andExpect(jsonPath("$.product_name").value("Mouse Gamer"))
                .andExpect(jsonPath("$.product_deleted").value(false))
                .andExpect(jsonPath("$.type").value("EXIT"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.description").value("Separacao de pedidos"))
                .andExpect(jsonPath("$.stock_after").value(20))
                .andExpect(jsonPath("$.created_at").value("2026-10-30T10:05:00"));

        ArgumentCaptor<CreateMovementRequest> captor = ArgumentCaptor.forClass(CreateMovementRequest.class);
        verify(stockMovementService).register(captor.capture());
        assertThat(captor.getValue().product_id()).isEqualTo(1L);
        assertThat(captor.getValue().type()).isEqualTo(MovementTypeEnum.EXIT);
        assertThat(captor.getValue().quantity()).isEqualTo(5);
    }

    @Test
    void post_semDescricao_eAceito() throws Exception {
        when(stockMovementService.register(any(CreateMovementRequest.class))).thenReturn(response(20, false));

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"ENTRY\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void post_descricaoCom255_eAceita_e256_retorna400() throws Exception {
        when(stockMovementService.register(any(CreateMovementRequest.class))).thenReturn(response(20, false));

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"ENTRY\",\"quantity\":1,\"description\":\"" + "d".repeat(255)
                        + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"ENTRY\",\"quantity\":1,\"description\":\"" + "d".repeat(256)
                        + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.description").exists());
    }

    @Test
    void post_quantidadeZero_retorna400_comFieldErrors() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"ENTRY\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validação falhou"))
                .andExpect(jsonPath("$.field_errors.quantity").value("A quantidade deve ser maior que zero"));
        verify(stockMovementService, never()).register(any());
    }

    @Test
    void post_quantidadeNegativaOuAusente_retorna400() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"EXIT\",\"quantity\":-2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.quantity").exists());

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"EXIT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.quantity").value("A quantidade é obrigatória"));
        verify(stockMovementService, never()).register(any());
    }

    @Test
    void post_productIdAusente_retorna400_comFieldErrors() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ENTRY\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validação falhou"))
                .andExpect(jsonPath("$.field_errors.product_id").value("O produto é obrigatório"));
        verify(stockMovementService, never()).register(any());
    }

    @Test
    void post_tipoAusente_retorna400_comFieldErrors() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.type").exists());
    }

    @Test
    void post_tipoInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":1,\"type\":\"TRANSFER\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
        verify(stockMovementService, never()).register(any());
    }

    @Test
    void post_jsonMalformado_retorna400() throws Exception {
        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content("{product_id:"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_produtoInexistenteOuExcluido_retorna404() throws Exception {
        when(stockMovementService.register(any(CreateMovementRequest.class)))
                .thenThrow(new ProductNotFoundException(1L));

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("produto não encontrado"))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
    }

    @Test
    void post_estoqueInsuficiente_retorna409_comSaldoEQuantidadeNaMensagem() throws Exception {
        when(stockMovementService.register(any(CreateMovementRequest.class)))
                .thenThrow(new InsufficientStockException("Mouse Gamer", 3, 5));

        mockMvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Estoque insuficiente"))
                .andExpect(jsonPath("$.message").value(
                        "Estoque insuficiente para o produto 'Mouse Gamer': saldo disponível 3, quantidade solicitada 5"))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
    }

    // ---------- GET /movements ----------

    @Test
    void get_semFiltros_repassaFiltroVazio_eRetornaStockAfterNulo() throws Exception {
        when(stockMovementService.search(any(MovementSearchFilter.class)))
                .thenReturn(List.of(response(null, false)));

        mockMvc.perform(get("/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].product_sku").value("MOUSE-001"))
                .andExpect(jsonPath("$[0].stock_after").value(org.hamcrest.Matchers.nullValue()));

        ArgumentCaptor<MovementSearchFilter> captor = ArgumentCaptor.forClass(MovementSearchFilter.class);
        verify(stockMovementService).search(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new MovementSearchFilter(null, null, null, null));
    }

    @Test
    void get_comTodosOsFiltros_repassaAoService() throws Exception {
        when(stockMovementService.search(any(MovementSearchFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/movements")
                .param("product_id", "1")
                .param("type", "ENTRY")
                .param("from", "2026-10-01T00:00:00")
                .param("to", "2026-10-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        ArgumentCaptor<MovementSearchFilter> captor = ArgumentCaptor.forClass(MovementSearchFilter.class);
        verify(stockMovementService).search(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new MovementSearchFilter(1L, MovementTypeEnum.ENTRY,
                LocalDateTime.of(2026, 10, 1, 0, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59, 59)));
    }

    @Test
    void get_listaVazia_retornaArrayVazio() throws Exception {
        when(stockMovementService.search(any(MovementSearchFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/movements"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void get_produtoExcluido_retorna200_comHistorico() throws Exception {
        when(stockMovementService.search(any(MovementSearchFilter.class)))
                .thenReturn(List.of(response(null, true)));

        mockMvc.perform(get("/movements").param("product_id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].product_deleted").value(true));
    }

    @Test
    void get_produtoInexistente_retorna404() throws Exception {
        when(stockMovementService.search(any(MovementSearchFilter.class)))
                .thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/movements").param("product_id", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("produto não encontrado"));
    }

    @Test
    void get_tipoInvalido_retorna400() throws Exception {
        mockMvc.perform(get("/movements").param("type", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"));
        verify(stockMovementService, never()).search(any());
    }

    @Test
    void get_dataInvalida_ouProductIdNaoNumerico_retorna400() throws Exception {
        mockMvc.perform(get("/movements").param("from", "ontem")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/movements").param("product_id", "abc")).andExpect(status().isBadRequest());
        verify(stockMovementService, never()).search(any());
    }
}
