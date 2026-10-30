package com.stockFlow.stockflow_api.integration.support;

import java.sql.SQLException;

/** Extrai o código de erro do MySQL (ex.: 1062 = duplicado, 3819 = CHECK) da cadeia de causas. */
public final class SqlErrors {

    private SqlErrors() {
    }

    public static int errorCode(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getErrorCode();
            }
            cause = cause.getCause();
        }
        throw new AssertionError("Nenhuma SQLException na cadeia de causas", error);
    }
}
