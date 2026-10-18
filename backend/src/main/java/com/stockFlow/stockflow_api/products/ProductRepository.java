package com.stockFlow.stockflow_api.products;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByNameIgnoreCaseOrSkuIgnoreCase(
            String name,
            String sku);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
}
