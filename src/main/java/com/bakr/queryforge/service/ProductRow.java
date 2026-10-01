package com.bakr.queryforge.service;

import java.math.BigDecimal;
import java.time.Instant;

/** Flat projection of a products row, shared by JPA (via mapper) and JDBC (keyset path). */
public record ProductRow(long id, String sku, String name, long categoryId, BigDecimal price,
                         int stockQuantity, String status, Instant createdAt, Instant updatedAt) {
}
