package com.stockFlow.stockflow_api.movements;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stockFlow.stockflow_api.movements.dto.MovementSearchFilter;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Sem banco: verifica os predicados montados (mocks da Criteria API). O SQL real
 * e a ordenação são cobertos por QA-03 (Testcontainers).
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class MovementsSpecificationsTest {

    private Root<Movements> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path productPath;
    private Path productIdPath;
    private Path typePath;
    private Path createdAtPath;

    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        productPath = mock(Path.class);
        productIdPath = mock(Path.class);
        typePath = mock(Path.class);
        createdAtPath = mock(Path.class);

        when(root.get("product")).thenReturn(productPath);
        when(productPath.get("id")).thenReturn(productIdPath);
        when(root.get("type")).thenReturn(typePath);
        when(root.get("createdAt")).thenReturn(createdAtPath);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
    }

    @Test
    void semFiltros_fazFetchJoinDoProduto_enaoFiltraNada_nemProdutosExcluidos() {
        MovementsSpecifications.from(new MovementSearchFilter(null, null, null, null)).toPredicate(root, query, cb);

        verify(root).fetch("product");
        verify(cb, never()).equal(any(Expression.class), any(Object.class));
        verify(cb, never()).greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class));
        verify(cb, never()).lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class));
        // histórico inclui produtos excluídos: nenhum acesso a deletedAt
        verify(productPath, never()).get("deletedAt");
    }

    @Test
    void comTodosOsFiltros_aplicaCadaPredicado() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 31, 23, 59);

        MovementsSpecifications.from(new MovementSearchFilter(1L, MovementTypeEnum.EXIT, from, to))
                .toPredicate(root, query, cb);

        verify(cb).equal(productIdPath, 1L);
        verify(cb).equal(typePath, MovementTypeEnum.EXIT);
        verify(cb).greaterThanOrEqualTo(createdAtPath, from);
        verify(cb).lessThanOrEqualTo(createdAtPath, to);
    }
}
