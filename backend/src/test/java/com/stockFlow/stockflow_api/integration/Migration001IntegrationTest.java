package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.stockFlow.stockflow_api.integration.support.MySqlTestContainer;
import com.stockFlow.stockflow_api.integration.support.RepositoryFiles;
import com.stockFlow.stockflow_api.integration.support.SqlErrors;

/**
 * QA-03 / DB-01 — migração 001 ({@code database/migrations/001_soft_delete_and_unique_name.sql})
 * aplicada sobre o schema ANTIGO (fixture {@code db/legacy/init_before_migration_001.sql}, cópia
 * literal de {@code git show HEAD:database/init.sql}, sem {@code deleted_at}/{@code uk_products_name}).
 *
 * Usa um container MySQL PRÓPRIO, isolado do contexto principal da aplicação (que usa o
 * init.sql novo). Antes de cada teste o banco {@code stockflow_db} DESTE container é recriado
 * (DROP/CREATE só aqui, nunca no container da aplicação).
 *
 * Não usa Spring: valida o script SQL em si.
 */
@Tag("integration")
@Testcontainers
class Migration001IntegrationTest {

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_DUP_FIELDNAME = 1060;

    private static final String PRODUCT_SNAPSHOT = "SELECT id, sku, name, description, price, quantity, min_stock, "
            + "created_at, updated_at FROM products ORDER BY id";
    private static final String MOVEMENT_SNAPSHOT = "SELECT * FROM stock_movements ORDER BY id";

    @Container
    static final MySQLContainer MYSQL = MySqlTestContainer.newContainer();

    private Connection connection;
    private JdbcTemplate jdbc;

    @BeforeEach
    void recreateLegacySchema() throws SQLException {
        connection = MySqlTestContainer.rootConnection(MYSQL);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.execute("DROP DATABASE IF EXISTS stockflow_db"); // somente neste container descartável
        populate(new ClassPathResource("db/legacy/init_before_migration_001.sql"));
        jdbc.execute("USE stockflow_db");
    }

    @AfterEach
    void closeConnection() throws SQLException {
        connection.close();
    }

    // ------------------------------------------------------------------ helpers

    private void populate(org.springframework.core.io.Resource script) {
        new ResourceDatabasePopulator(false, false, "UTF-8", script).execute(
                new SingleConnectionDataSource(connection, true));
    }

    private void applyMigration() {
        populate(new FileSystemResource(RepositoryFiles.databaseFile("migrations/001_soft_delete_and_unique_name.sql")));
    }

    private void insertProduct(String sku, String name) {
        jdbc.update("INSERT INTO products (sku, name, description, price, quantity, min_stock) VALUES (?,?,?,?,?,?)",
                sku, name, "legado", 12.34, 7, 2);
    }

    private int columnCount(String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'stockflow_db' AND table_name = 'products' AND column_name = ?",
                Integer.class, column);
    }

    private int indexCount(String indexName) {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                + "WHERE table_schema = 'stockflow_db' AND table_name = 'products' AND index_name = ?",
                Integer.class, indexName);
    }

    private List<Map<String, Object>> productSnapshot() {
        return jdbc.queryForList(PRODUCT_SNAPSHOT);
    }

    private List<Map<String, Object>> movementSnapshot() {
        return jdbc.queryForList(MOVEMENT_SNAPSHOT);
    }

    // ------------------------------------------------------------------ cenários

    @Test
    void fixtureLegadaRepresentaOSchemaAntigo_semDeletedAtENemUniqueDeNome() {
        assertThat(columnCount("deleted_at")).isZero();
        assertThat(indexCount("uk_products_name")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(3);
    }

    @Test
    void migracao_aplicaSobreSchemaAntigo_preservaDados_criaDeletedAtNuloEUniqueDeNome() {
        insertProduct("LEGACY-A", "Produto legado A");
        jdbc.update("INSERT INTO stock_movements (product_id, type, quantity, description) "
                + "SELECT id, 'ENTRY', 7, 'legado' FROM products WHERE sku = 'LEGACY-A'");
        List<Map<String, Object>> productsBefore = productSnapshot();
        List<Map<String, Object>> movementsBefore = movementSnapshot();
        assertThat(productsBefore).hasSize(4);

        applyMigration();

        // coluna criada, anulável, NULL em todos os registros antigos
        assertThat(columnCount("deleted_at")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = 'stockflow_db' AND table_name = 'products' AND column_name = 'deleted_at'",
                String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE deleted_at IS NULL", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE deleted_at IS NOT NULL", Integer.class))
                .isZero();

        // dados preservados (nenhuma linha removida nem alterada, inclusive updated_at)
        assertThat(productSnapshot()).isEqualTo(productsBefore);
        assertThat(movementSnapshot()).isEqualTo(movementsBefore);

        // UNIQUE de nome criado e efetivo
        assertThat(jdbc.queryForObject("SELECT non_unique FROM information_schema.statistics "
                + "WHERE table_schema = 'stockflow_db' AND table_name = 'products' AND index_name = 'uk_products_name'",
                Integer.class)).isZero();
        assertThatThrownBy(() -> insertProduct("LEGACY-B", "Produto legado A"))
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isEqualTo(ER_DUP_ENTRY));

        // novos registros nascem ativos (deleted_at NULL por padrão) e o FK continua valendo
        insertProduct("LEGACY-C", "Produto legado C");
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM products WHERE sku = 'LEGACY-C'", java.sql.Timestamp.class))
                .isNull();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_movements (product_id, type, quantity) VALUES (987654321, 'ENTRY', 1)"))
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isEqualTo(1452));
    }

    @Test
    void migracao_naoEIdempotente_segundaExecucaoFalha_semAlterarNada() {
        applyMigration();
        List<Map<String, Object>> productsAfterFirst = productSnapshot();
        List<Map<String, Object>> movementsAfterFirst = movementSnapshot();

        assertThatThrownBy(this::applyMigration)
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isIn(ER_DUP_FIELDNAME, ER_DUP_ENTRY, 1061));

        assertThat(columnCount("deleted_at")).isEqualTo(1);
        assertThat(indexCount("uk_products_name")).isEqualTo(1);
        assertThat(productSnapshot()).isEqualTo(productsAfterFirst);
        assertThat(movementSnapshot()).isEqualTo(movementsAfterFirst);
    }

    @Test
    void migracao_comNomesDuplicados_falha_eNadaEAlterado_colunaNaoECriada() {
        insertProduct("DUP-1", "Caneta Azul");
        insertProduct("DUP-2", "Caneta Azul"); // duplicado exato (permitido no schema antigo)
        List<Map<String, Object>> productsBefore = productSnapshot();
        List<Map<String, Object>> movementsBefore = movementSnapshot();

        assertThatThrownBy(this::applyMigration)
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isEqualTo(ER_DUP_ENTRY));

        // ALTER TABLE único e atômico: nem a coluna nem o índice existem; dados idênticos
        assertThat(columnCount("deleted_at")).as("coluna deleted_at NÃO deve existir").isZero();
        assertThat(indexCount("uk_products_name")).as("índice uk_products_name NÃO deve existir").isZero();
        assertThat(productSnapshot()).isEqualTo(productsBefore);
        assertThat(movementSnapshot()).isEqualTo(movementsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE name = 'Caneta Azul'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void migracao_detectaDuplicidadePelaCollation_caixaEEspacoFinalContamComoIguais() {
        insertProduct("DUP-1", "Caneta Verde");
        insertProduct("DUP-2", "caneta verde "); // utf8mb4_unicode_ci: sem caixa e sem espaço final
        List<Map<String, Object>> productsBefore = productSnapshot();

        assertThatThrownBy(this::applyMigration)
                .satisfies(ex -> assertThat(SqlErrors.errorCode(ex)).isEqualTo(ER_DUP_ENTRY));

        assertThat(columnCount("deleted_at")).isZero();
        assertThat(indexCount("uk_products_name")).isZero();
        assertThat(productSnapshot()).isEqualTo(productsBefore);
    }

    @Test
    void schemaMigrado_equivaleAoDoInitSqlNovo_colunasIndicesEChecks() throws Exception {
        applyMigration();
        // referência: schema criado pelo database/init.sql atual no container principal da aplicação
        try (Connection reference = MySqlTestContainer.rootConnection(MySqlTestContainer.get())) {
            JdbcTemplate referenceJdbc = new JdbcTemplate(new SingleConnectionDataSource(reference, true));

            assertThat(shape(jdbc, COLUMNS_SQL)).isEqualTo(shape(referenceJdbc, COLUMNS_SQL));
            assertThat(shape(jdbc, INDEXES_SQL)).isEqualTo(shape(referenceJdbc, INDEXES_SQL));
            assertThat(shape(jdbc, CHECKS_SQL)).isEqualTo(shape(referenceJdbc, CHECKS_SQL));
        }
    }

    private static final String COLUMNS_SQL = "SELECT table_name, column_name, ordinal_position, column_type, is_nullable, "
            + "column_default, extra, collation_name FROM information_schema.columns "
            + "WHERE table_schema = 'stockflow_db' ORDER BY table_name, ordinal_position";

    private static final String INDEXES_SQL = "SELECT table_name, index_name, non_unique, seq_in_index, column_name "
            + "FROM information_schema.statistics WHERE table_schema = 'stockflow_db' "
            + "ORDER BY table_name, index_name, seq_in_index";

    private static final String CHECKS_SQL = "SELECT constraint_name, check_clause FROM information_schema.check_constraints "
            + "WHERE constraint_schema = 'stockflow_db' ORDER BY constraint_name";

    private static List<Map<String, Object>> shape(JdbcTemplate template, String sql) {
        return template.queryForList(sql);
    }
}
