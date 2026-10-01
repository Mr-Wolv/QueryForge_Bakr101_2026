package com.bakr.queryforge.service;

import com.bakr.queryforge.entity.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic filtered-search predicate for the baseline (offset) workload.
 * One SQL statement with only the predicates the client actually supplied —
 * no in-memory filtering of a full table load.
 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<Product> search(Long categoryId, String status, BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (categoryId != null) {
                preds.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (minPrice != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
