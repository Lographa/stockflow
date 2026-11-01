package com.stockFlow.stockflow_api.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.stockFlow.stockflow_api.products.dto.CreateProductRequest;
import com.stockFlow.stockflow_api.products.dto.ProductResponse;
import com.stockFlow.stockflow_api.products.dto.ProductResponseAll;
import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;
import com.stockFlow.stockflow_api.products.dto.UpdateProductRequest;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.exception.SameProductException;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProductControllerTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 10, 30, 10, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static ProductResponse response() {
        return new ProductResponse(1L, "MOUSE-001", "Mouse Gamer", "desc", new BigDecimal("129.90"),
                25, 10, false, TS, TS);
    }

    private static final String VALID_CREATE = """
            {"sku":"MOUSE-001","name":"Mouse Gamer","description":"desc","price":129.90,"quantity":25,"min_stock":10}
            """;

    // ---------- POST /product/create ----------

    @Test
    void create_valido_retorna201ComRespostaCompleta() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("MOUSE-001"))
                .andExpect(jsonPath("$.name").value("Mouse Gamer"))
                .andExpect(jsonPath("$.description").value("desc"))
                .andExpect(jsonPath("$.price").value(129.90))
                .andExpect(jsonPath("$.quantity").value(25))
                .andExpect(jsonPath("$.min_stock").value(10))
                .andExpect(jsonPath("$.low_stock").value(false))
                .andExpect(jsonPath("$.created_at").value("2026-10-30T10:00:00"))
                .andExpect(jsonPath("$.updated_at").value("2026-10-30T10:00:00"));
    }

    @Test
    void create_precoZero_quantidadeZero_minStockZero_saoAceitos() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"B","price":0,"quantity":0,"min_stock":0}
                """))
                .andExpect(status().isCreated());
    }

    @Test
    void create_precoNegativo_retorna400ComFieldErrors() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"B","price":-1,"quantity":1,"min_stock":1}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validação falhou"))
                .andExpect(jsonPath("$.field_errors.price").value("O preço não pode ser negativo"));
        verify(productService, never()).createProduct(any());
    }

    @Test
    void create_precoComMaisDeDuasCasasOuInteirosDemais_retorna400() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"B","price":1.234,"quantity":1,"min_stock":1}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.price").exists());

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"B","price":123456789,"quantity":1,"min_stock":1}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.price").exists());
    }

    @Test
    void create_quantidadeENegativos_retorna400() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"B","price":1,"quantity":-1,"min_stock":-5}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.quantity").value("A quantidade não pode ser negativa"))
                .andExpect(jsonPath("$.field_errors.min_stock").value("O estoque mínimo não pode ser negativo"));
    }

    @Test
    void create_camposObrigatoriosAusentes_retorna400() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.sku").exists())
                .andExpect(jsonPath("$.field_errors.name").exists())
                .andExpect(jsonPath("$.field_errors.price").exists())
                .andExpect(jsonPath("$.field_errors.quantity").exists())
                .andExpect(jsonPath("$.field_errors.min_stock").exists());
    }

    @Test
    void create_nomeVazioESkuCom51Caracteres_retorna400() throws Exception {
        String sku51 = "S".repeat(51);
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"%s","name":"  ","price":1,"quantity":1,"min_stock":1}
                """.formatted(sku51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.sku").value("O sku deve ter no máximo 50 caracteres"))
                .andExpect(jsonPath("$.field_errors.name").value("O nome é obrigatório"));
    }

    @Test
    void create_nomeComMaisDe150_descricaoComMaisDe500_retorna400() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"%s","description":"%s","price":1,"quantity":1,"min_stock":1}
                """.formatted("n".repeat(151), "d".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.name").exists())
                .andExpect(jsonPath("$.field_errors.description").exists());
    }

    @Test
    void create_limitesExatos_saoAceitos() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"%s","name":"%s","description":"%s","price":99999999.99,"quantity":0,"min_stock":0}
                """.formatted("s".repeat(50), "n".repeat(150), "d".repeat(500))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_jsonMalformado_retorna400() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("{sku:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
    }

    @Test
    void create_tipoInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"A","name":"B","price":1,"quantity":"abc","min_stock":1}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicado_retorna409() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenThrow(new SameProductException("Mouse Gamer", "MOUSE-001"));

        mockMvc.perform(post("/product/create").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Produto já cadastrado com nome ou sku"));
    }

    // ---------- PUT /product/{id} ----------

    @Test
    void update_corpoVazio_eAceito_eRetornaRespostaCompleta() throws Exception {
        when(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).thenReturn(response());

        mockMvc.perform(put("/product/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.low_stock").value(false))
                .andExpect(jsonPath("$.min_stock").value(10));
    }

    @Test
    void update_precoZero_eAceito() throws Exception {
        when(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).thenReturn(response());

        mockMvc.perform(put("/product/1").contentType(MediaType.APPLICATION_JSON).content("{\"price\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void update_camposInformadosInvalidos_retorna400() throws Exception {
        mockMvc.perform(put("/product/1").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"","name":"   ","description":"","price":-1,"quantity":-1,"min_stock":-1}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.sku").value("O sku não pode ser vazio"))
                .andExpect(jsonPath("$.field_errors.name").value("O nome não pode ser vazio"))
                .andExpect(jsonPath("$.field_errors.description").value("A descrição não pode ser vazia"))
                .andExpect(jsonPath("$.field_errors.price").value("O preço não pode ser negativo"))
                .andExpect(jsonPath("$.field_errors.quantity").exists())
                .andExpect(jsonPath("$.field_errors.min_stock").exists());
        verify(productService, never()).updateProduct(any(), any());
    }

    @Test
    void update_skuCom51_retorna400() throws Exception {
        mockMvc.perform(put("/product/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.sku").exists());
    }

    @Test
    void update_inexistenteOuExcluido_retorna404() throws Exception {
        when(productService.updateProduct(eq(9L), any(UpdateProductRequest.class)))
                .thenThrow(new ProductNotFoundException(9L));

        mockMvc.perform(put("/product/9").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    // ---------- GET ----------

    @Test
    void getById_retornaRespostaCompleta() throws Exception {
        when(productService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("MOUSE-001"))
                .andExpect(jsonPath("$.name").value("Mouse Gamer"))
                .andExpect(jsonPath("$.quantity").value(25))
                .andExpect(jsonPath("$.min_stock").value(10))
                .andExpect(jsonPath("$.low_stock").value(false))
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    void getById_inexistenteOuExcluido_retorna404() throws Exception {
        when(productService.findById(5L)).thenThrow(new ProductNotFoundException(5L));

        mockMvc.perform(get("/product/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("produto não encontrado"))
                .andExpect(jsonPath("$.field_errors").doesNotExist());
    }

    @Test
    void getById_idNaoNumerico_retorna400() throws Exception {
        mockMvc.perform(get("/product/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"));
    }

    @Test
    void getAll_semProdutos_retornaListaVazia() throws Exception {
        when(productService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getAll_retornaItensComMesmosCamposDoDetalhe() throws Exception {
        when(productService.findAll()).thenReturn(List.of(new ProductResponseAll(2L, "KEYBOARD-001",
                "Teclado Mecanico", "d", new BigDecimal("249.90"), 8, 10, true, TS, TS)));

        mockMvc.perform(get("/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("KEYBOARD-001"))
                .andExpect(jsonPath("$[0].name").value("Teclado Mecanico"))
                .andExpect(jsonPath("$[0].quantity").value(8))
                .andExpect(jsonPath("$[0].min_stock").value(10))
                .andExpect(jsonPath("$[0].low_stock").value(true));
    }

    // ---------- search ----------

    @Test
    void search_lowStockTrue_repassaFiltroAoService() throws Exception {
        when(productService.search(any(ProductSearchFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/product/search").param("lowStock", "true").param("q", "mouse"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        ArgumentCaptor<ProductSearchFilter> captor = ArgumentCaptor.forClass(ProductSearchFilter.class);
        verify(productService).search(captor.capture());
        assertThat(captor.getValue().lowStock()).isTrue();
        assertThat(captor.getValue().q()).isEqualTo("mouse");
    }

    @Test
    void search_semLowStock_filtroNulo() throws Exception {
        when(productService.search(any(ProductSearchFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/product/search")).andExpect(status().isOk());

        ArgumentCaptor<ProductSearchFilter> captor = ArgumentCaptor.forClass(ProductSearchFilter.class);
        verify(productService).search(captor.capture());
        assertThat(captor.getValue().lowStock()).isNull();
    }

    @Test
    void search_lowStockInvalido_retorna400() throws Exception {
        mockMvc.perform(get("/product/search").param("lowStock", "talvez"))
                .andExpect(status().isBadRequest());
        verify(productService, never()).search(any());
    }

    // ---------- DELETE ----------

    @Test
    void delete_retorna204SemCorpo() throws Exception {
        mockMvc.perform(delete("/product/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(productService).deleteProduct(1L);
    }

    @Test
    void delete_inexistenteOuJaExcluido_retorna404() throws Exception {
        doThrow(new ProductNotFoundException(8L)).when(productService).deleteProduct(8L);

        mockMvc.perform(delete("/product/8"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("produto não encontrado"));
    }
}
