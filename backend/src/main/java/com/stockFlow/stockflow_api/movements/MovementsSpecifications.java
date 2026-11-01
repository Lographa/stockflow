package com.stockFlow.stockflow_api.movements;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.stockFlow.stockflow_api.movements.dto.MovementSearchFilter;

import jakarta.persistence.criteria.Predicate;

public final class MovementsSpecifications {

    private MovementsSpecifications() {
    }

    /**
     * Filtros opcionais do histórico. Faz fetch join de {@code product} (evita N+1 e
     * LazyInitializationException ao montar o MovementResponse). NÃO filtra
     * {@code deleted_at}: o histórico inclui movimentações de produtos excluídos.
     */
    public static Specification<Movements> from(MovementSearchFilter f) {
        return (root, query, cb) -> {
            root.fetch("product");

            List<Predicate> predicates = new ArrayList<>();

            if (f.productId() != null) {
                predicates.add(cb.equal(root.get("product").get("id"), f.productId()));
            }
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.to()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
