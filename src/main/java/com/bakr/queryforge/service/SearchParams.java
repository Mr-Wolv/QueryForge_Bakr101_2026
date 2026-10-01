package com.bakr.queryforge.service;

import com.bakr.queryforge.repo.ProductKeysetRepository;

import java.math.BigDecimal;

/**
 * Validated search parameters. Construction throws IllegalArgumentException for
 * anything invalid; the exception handler maps that to HTTP 400.
 */
public record SearchParams(Long categoryId, String status, BigDecimal minPrice, BigDecimal maxPrice,
                           String sortField, boolean asc, int page, int size) {

    public static final int MAX_SIZE = 200;

    public SearchParams {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        if (status != null && status.isBlank()) status = null;
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must be <= maxPrice");
        }
        if (sortField == null || sortField.isBlank()) sortField = "createdAt";
        else {
            // validates via whitelist; store canonical camelCase name
            ProductKeysetRepository.validatedColumn(sortField);
            sortField = switch (sortField.toLowerCase()) {
                case "price" -> "price";
                case "id" -> "id";
                default -> "createdAt";
            };
        }
    }
}
