package com.bakr.queryforge.repo;

import com.bakr.queryforge.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Stage 0 baseline: plain Spring Data JPA. No fetch graphs, no native SQL,
 * no hints — the point of the baseline is to measure the default behavior.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
}
