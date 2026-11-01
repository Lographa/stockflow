package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.stockFlow.stockflow_api.integration.support.MySqlTestContainer;

/**
 * Base de integração (QA-03 / D-5 / T-08): contexto Spring COMPLETO contra um
 * MySQL 8.4 real (Testcontainers) cujo schema vem de {@code database/init.sql},
 * com {@code ddl-auto=validate} (prova que entidades × schema coincidem).
 *
 * <ul>
 * <li>Perfil {@code test} (API aberta, D-9).</li>
 * <li>Redis NÃO é usado pela aplicação: aponta para uma porta inexistente para
 * garantir que o contexto sobe sem Redis (e que ninguém dependa de um Redis
 * local por acidente).</li>
 * <li>Isolamento: cada teste cria seus próprios produtos (SKU/nome únicos) e, ao
 * final, remove somente o que ele mesmo criou. Os seeds (MOUSE-001, ...) nunca
 * são tocados e nenhuma tabela é truncada.</li>
 * </ul>
 *
 * Sem Docker, os testes FALHAM de forma explícita (ver {@link MySqlTestContainer}).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractMySqlIntegrationTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        MySQLContainer mysql = MySqlTestContainer.get();
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Value("${local.server.port}")
    private int port;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Produtos criados pelo teste corrente (removidos no @AfterEach). */
    private final List<Long> createdProductIds = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpOwnData() {
        for (Long id : createdProductIds) {
            jdbc.update("DELETE FROM stock_movements WHERE product_id = ?", id);
            jdbc.update("DELETE FROM products WHERE id = ?", id);
        }
        createdProductIds.clear();
    }

    // ------------------------------------------------------------------ dados únicos

    protected static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected static String newSku() {
        return "IT-" + unique();
    }

    protected static String newName() {
        return "IT Produto " + unique();
    }

    protected static String productJson(String sku, String name, int quantity, int minStock) {
        return """
                {"sku":"%s","name":"%s","description":"Produto de teste de integracao","price":10.50,"quantity":%d,"min_stock":%d}
                """.formatted(sku, name, quantity, minStock);
    }

    protected static String movementJson(long productId, String type, int quantity) {
        return """
                {"product_id":%d,"type":"%s","quantity":%d}
                """.formatted(productId, type, quantity);
    }

    // ------------------------------------------------------------------ criação / rastreio

    /** Marca um produto (criado por SQL direto, por exemplo) para limpeza ao final do teste. */
    protected void track(long productId) {
        createdProductIds.add(productId);
    }

    /** Cria produto pela API (MockMvc, camada HTTP completa), exige 201 e rastreia para limpeza. */
    protected long createProduct(int quantity, int minStock) throws Exception {
        return createProduct(newSku(), newName(), quantity, minStock);
    }

    protected long createProduct(String sku, String name, int quantity, int minStock) throws Exception {
        String body = mockMvc.perform(post("/product/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, name, quantity, minStock)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        track(id);
        return id;
    }

    // ------------------------------------------------------------------ consultas SQL reais

    protected int quantityOf(long productId) {
        return jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, productId);
    }

    protected int movementCount(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", Integer.class,
                productId);
    }

    protected int movementCount(long productId, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id = ? AND type = ?",
                Integer.class, productId, type);
    }

    protected int sumOf(long productId, String type) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE product_id = ? AND type = ?",
                Integer.class, productId, type);
    }

    protected int productRows(String sku) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE sku = ?", Integer.class, sku);
    }

    /** Invariante transversal 2: products.quantity == Σ ENTRY − Σ EXIT (consulta SQL real) e nunca negativo. */
    protected void assertBalanceMatchesHistory(long productId) {
        int balance = quantityOf(productId);
        int entries = sumOf(productId, "ENTRY");
        int exits = sumOf(productId, "EXIT");
        assertThat(balance)
                .as("saldo do produto %d == Σ ENTRY (%d) − Σ EXIT (%d)", productId, entries, exits)
                .isEqualTo(entries - exits)
                .isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------ HTTP real (Tomcat embutido)

    /** Resposta mínima de uma chamada HTTP real. */
    protected record HttpResult(int status, String body) {
    }

    protected HttpResult httpPost(String path, String json) {
        return httpSend(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    protected HttpResult httpPut(String path, String json) {
        return httpSend(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    protected HttpResult httpDelete(String path) {
        return httpSend(HttpRequest.newBuilder(uri(path)).DELETE());
    }

    protected HttpResult httpGet(String path) {
        return httpSend(HttpRequest.newBuilder(uri(path)).GET());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResult httpSend(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = HTTP.send(builder.timeout(Duration.ofSeconds(90)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Falha de I/O na chamada HTTP", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
