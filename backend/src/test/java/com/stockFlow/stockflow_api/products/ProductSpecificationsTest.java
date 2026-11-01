package com.stockFlow.stockflow_api.products;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Teste sem banco: verifica quais predicados a Specification monta, usando mocks
 * da Criteria API. O comportamento real em SQL é coberto por QA-03 (Testcontainers).
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class ProductSpecificationsTest {

    private Root<Product> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path deletedAtPath;
    private Path quantityPath;
    private Path minStockPath;
    private Predicate isNullPredicate;
    private Predicate lessThanPredicate;
    private Predicate andPredicate;

    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        deletedAtPath = mock(Path.class);
        quantityPath = mock(Path.class);
        minStockPath = mock(Path.class);
        isNullPredicate = mock(Predicate.class);
        lessThanPredicate = mock(Predicate.class);
        andPredicate = mock(Predicate.class);

        when(root.get("deletedAt")).thenReturn(deletedAtPath);
        when(root.get("quantity")).thenReturn(quantityPath);
        when(root.get("min_stock")).thenReturn(minStockPath);
        when(cb.isNull(deletedAtPath)).thenReturn(isNullPredicate);
        when(cb.lessThan(any(jakarta.persistence.criteria.Expression.class),
                any(jakarta.persistence.criteria.Expression.class))).thenReturn(lessThanPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);
    }

    @Test
    void sempreFiltraApenasAtivos_deletedAtIsNull() {
        Specification<Product> spec = ProductSpecifications.from(new ProductSearchFilter(null, null, null, null, null));

        spec.toPredicate(root, query, cb);

        verify(cb).isNull(deletedAtPath);
        verify(cb, never()).lessThan(any(jakarta.persistence.criteria.Expression.class),
                any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void lowStockFalse_naoAplicaFiltroDeEstoqueBaixo() {
        ProductSpecifications.from(new ProductSearchFilter(null, null, null, null, false))
                .toPredicate(root, query, cb);

        verify(cb).isNull(deletedAtPath);
        verify(cb, never()).lessThan(any(jakarta.persistence.criteria.Expression.class),
                any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void lowStockTrue_filtraQuantityMenorQueMinStock() {
        ProductSpecifications.from(new ProductSearchFilter(null, null, null, null, true))
                .toPredicate(root, query, cb);

        verify(cb).isNull(deletedAtPath);
        verify(cb).lessThan(quantityPath, minStockPath);
    }
}
