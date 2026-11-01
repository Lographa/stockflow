package com.stockFlow.stockflow_api.integration.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Container MySQL 8.4 ÚNICO (singleton) compartilhado por todas as classes de
 * integração da JVM (o contexto Spring é cacheado entre classes, então a URL do
 * banco não pode mudar). Sobe sob demanda e é removido pelo Ryuk ao fim da JVM.
 *
 * O schema vem de {@code database/init.sql} (T-08): mesmo DDL de produção, com
 * CHECK/FK/UNIQUE e os produtos seed. Nada é duplicado nos testes.
 *
 * Sem Docker disponível a falha é EXPLÍCITA (não há skip silencioso).
 */
public final class MySqlTestContainer {

    public static final String IMAGE = "mysql:8.4";
    public static final String DATABASE = "stockflow_db";
    public static final String USER = "test";
    public static final String PASSWORD = "test";

    private static MySQLContainer container;

    private MySqlTestContainer() {
    }

    public static synchronized MySQLContainer get() {
        if (container == null) {
            container = startWithInitSql();
        }
        return container;
    }

    /** Conexão como root (DDL/consultas administrativas dos testes). */
    public static Connection rootConnection(MySQLContainer mysql) throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
    }

    /** Cria o container MySQL com as credenciais padrão dos testes (sem iniciar). */
    public static MySQLContainer newContainer() {
        return new MySQLContainer(IMAGE)
                .withDatabaseName(DATABASE)
                .withUsername(USER)
                .withPassword(PASSWORD);
    }

    /** Verifica o Docker e falha com mensagem clara (nunca ignora o teste). */
    public static void requireDocker() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            throw new IllegalStateException(dockerMessage(), ex);
        }
        if (!available) {
            throw new IllegalStateException(dockerMessage());
        }
    }

    private static String dockerMessage() {
        return "Docker indisponível: os testes de integração (@Tag(\"integration\")) exigem Docker Desktop em "
                + "execução (Testcontainers + MySQL real, D-5/D-10). Inicie o Docker Desktop ou execute apenas "
                + "os unitários com: .\\mvnw.cmd test -DexcludedGroups=integration";
    }

    private static MySQLContainer startWithInitSql() {
        requireDocker();
        MySQLContainer mysql = newContainer();
        mysql.start();
        applyInitSql(mysql);
        return mysql;
    }

    private static void applyInitSql(MySQLContainer mysql) {
        Path initSql = RepositoryFiles.databaseFile("init.sql");
        try (Connection connection = rootConnection(mysql)) {
            // Mesmo charset/collation do init.sql de produção (o banco já foi criado pelo container).
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER DATABASE " + DATABASE
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            new ResourceDatabasePopulator(false, false, "UTF-8", new FileSystemResource(initSql))
                    .populate(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Falha ao aplicar " + initSql + " no container MySQL", ex);
        }
    }
}
