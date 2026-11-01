package com.stockFlow.stockflow_api.products.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filtros opcionais da busca. {@code lowStock = true} restringe a produtos com
 * {@code quantity < min_stock}; {@code false}/{@code null} não aplica filtro.
 */
public record ProductSearchFilter(List<Long> ids,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        String q,
        Boolean lowStock) {

}
