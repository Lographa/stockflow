package com.stockFlow.stockflow_api.products;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.stockFlow.stockflow_api.products.dto.ProductSearchFilter;

import jakarta.persistence.criteria.Predicate;

public final class ProductSpecifications {

    public static Specification<Product> from(ProductSearchFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f.ids() != null && !f.ids().isEmpty()) {
                predicates.add(root.get("id").in(f.ids()));
            }

            if (f.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("created_at"), f.createdFrom()));
            }
            if (f.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("created_at"), f.createdTo()));
            }

            if (f.q() != null && !f.q().isBlank()) {
                String pattern = "%" + f.q().trim().toLowerCase() + "%";
                Predicate inName = cb.like(cb.lower(root.get("name")), pattern);
                Predicate inSku = cb.like(cb.lower(root.get("sku")), pattern);
                Predicate inDesc = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(inName, inSku, inDesc));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
