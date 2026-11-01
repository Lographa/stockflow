package com.stockFlow.stockflow_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.stockFlow.stockflow_api.integration.support.RepositoryFiles;

/**
 * Verificações ESTÁTICAS (sem Docker) dos scripts SQL usados pelos testes de
 * integração (QA-01/QA-03): garante que o parser de scripts do Spring enxerga os
 * comandos esperados e que a migração 001 respeita as regras do projeto (sem DDL/DML
 * destrutivo; alteração única e atômica). A execução real contra MySQL está em
 * {@code Migration001IntegrationTest}.
 */
class DatabaseScriptsStaticTest {

    private static final Pattern DESTRUCTIVE = Pattern.compile("\\b(DROP|DELETE|TRUNCATE)\\b",
            Pattern.CASE_INSENSITIVE);

    private static List<String> statementsOf(Path script) throws IOException {
        return split(Files.readString(script, StandardCharsets.UTF_8));
    }

    /**
     * Executa o script com o MESMO parser dos testes de integração
     * ({@link ResourceDatabasePopulator}) contra uma Connection falsa, apenas capturando
     * os comandos que seriam enviados ao MySQL.
     */
    private static List<String> split(String script) {
        try {
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            // -1 = "sem mais resultados" (o ScriptUtils itera até getUpdateCount() == -1)
            when(statement.getUpdateCount()).thenReturn(-1);
            List<String> executed = new ArrayList<>();
            when(statement.execute(anyString())).thenAnswer(invocation -> {
                executed.add(invocation.getArgument(0, String.class).trim());
                return false;
            });
            new ResourceDatabasePopulator(false, false, "UTF-8",
                    new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8))).populate(connection);
            return executed;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
    private static List<String> legacyFixtureStatements() throws IOException {
        try (var in = DatabaseScriptsStaticTest.class.getResourceAsStream("/db/legacy/init_before_migration_001.sql")) {
            assertThat(in).as("fixture db/legacy/init_before_migration_001.sql").isNotNull();
            return split(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void migracao001_temDiagnosticoSelect_eUmUnicoAlterTableAtomicoComColunaEUnique() throws IOException {
        List<String> statements = statementsOf(RepositoryFiles.databaseFile("migrations/001_soft_delete_and_unique_name.sql"));

        // comentários com ';' não podem gerar comandos fantasmas: apenas SELECT + ALTER
        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).startsWith("SELECT");
        assertThat(statements.get(1))
                .startsWith("ALTER TABLE products")
                .contains("ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL")
                .contains("ADD CONSTRAINT uk_products_name UNIQUE (name)");
    }

    @Test
    void migracao001_naoContemComandosDestrutivos() throws IOException {
        List<String> statements = statementsOf(RepositoryFiles.databaseFile("migrations/001_soft_delete_and_unique_name.sql"));

        assertThat(statements).noneMatch(statement -> DESTRUCTIVE.matcher(statement).find());
    }

    @Test
    void initSqlAtual_criaBancoTabelasComSoftDeleteUniqueDeNomeCheckEFk() throws IOException {
        List<String> statements = statementsOf(RepositoryFiles.databaseFile("init.sql"));
        String all = String.join(";\n", statements);

        assertThat(statements.get(0)).startsWith("CREATE DATABASE IF NOT EXISTS stockflow_db");
        assertThat(statements.get(1)).isEqualTo("USE stockflow_db");
        assertThat(all)
                .contains("deleted_at TIMESTAMP NULL DEFAULT NULL")
                .contains("CONSTRAINT uk_products_name UNIQUE (name)")
                .contains("CONSTRAINT chk_movement_quantity")
                .contains("CHECK (quantity > 0)")
                .contains("FOREIGN KEY (product_id)");
    }

    @Test
    void fixtureLegada_representaOSchemaAntigo_semDeletedAtENemUniqueDeNome() throws IOException {
        String all = String.join(";\n", legacyFixtureStatements());

        assertThat(all).contains("CREATE TABLE IF NOT EXISTS products")
                .contains("CREATE TABLE IF NOT EXISTS stock_movements")
                .doesNotContain("deleted_at")
                .doesNotContain("uk_products_name");
    }
}
