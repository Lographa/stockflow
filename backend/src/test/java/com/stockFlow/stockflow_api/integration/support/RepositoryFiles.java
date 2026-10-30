package com.stockFlow.stockflow_api.integration.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Localiza arquivos da pasta {@code database/} do repositório (fonte única do
 * schema — T-08) de forma robusta: sobe a partir do diretório de trabalho até
 * encontrar {@code database/<relativo>}. Funciona via Maven (cwd = {@code backend/},
 * ou seja, {@code ../database/...}) e via IDE (cwd = módulo ou raiz do repositório).
 */
public final class RepositoryFiles {

    private RepositoryFiles() {
    }

    public static Path databaseFile(String relativePath) {
        Path dir = Path.of("").toAbsolutePath();
        Path searchedFrom = dir;
        while (dir != null) {
            Path candidate = dir.resolve("database").resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Arquivo database/" + relativePath
                + " não encontrado subindo a partir de " + searchedFrom);
    }
}
