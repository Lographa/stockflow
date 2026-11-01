package com.stockFlow.stockflow_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockFlow.stockflow_api.integration.AbstractMySqlIntegrationTest;

/**
 * Conexão com o MySQL real do Testcontainers (não depende mais de MySQL manual).
 * Referenciado por {@code comandos/test-db.bat}.
 */
@Tag("integration")
class DatabaseConnectionTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConnectToDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(
                    connection.isValid(2),
                    "A conexão com o banco deve ser válida");
        }
    }

    @Test
    void conectaNoMySql8_comBancoEschemaDoInitSql() {
        String version = jdbc.queryForObject("SELECT VERSION()", String.class);
        assertTrue(version.startsWith("8."), "MySQL 8.x esperado, obtido: " + version);
        assertEquals("stockflow_db", jdbc.queryForObject("SELECT DATABASE()", String.class));

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'stockflow_db'",
                String.class);
        assertTrue(tables.containsAll(List.of("products", "stock_movements")), "Tabelas do init.sql: " + tables);
    }

    @Test
    void initSql_carregouProdutosSeed_semDependerDeOutrosTestes() {
        // Seeds nunca são alterados/removidos pelos testes de integração.
        Integer seeds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE sku IN ('MOUSE-001','KEYBOARD-001','MONITOR-001')",
                Integer.class);
        assertEquals(3, seeds);
    }
}
