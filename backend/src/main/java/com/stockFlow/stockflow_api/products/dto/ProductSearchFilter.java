package com.stockFlow.stockflow_api.products.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductSearchFilter(List<Long> ids,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        String q) {

}
