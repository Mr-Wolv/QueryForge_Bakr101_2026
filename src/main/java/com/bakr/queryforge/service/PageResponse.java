package com.bakr.queryforge.service;

import java.util.List;

/**
 * Stable pagination envelope shared by list endpoints.
 *
 * <p>{@code totalPages} is derived from {@code totalElements} and the clamped
 * page size so clients never have to duplicate the rounding rule.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int safeSize = Math.max(1, size);
        int totalPages = (int) ((totalElements + safeSize - 1) / safeSize);
        boolean hasNext = (long) Math.max(0, page) * safeSize + content.size() < totalElements;
        return new PageResponse<>(content, Math.max(0, page), safeSize, totalElements, totalPages, hasNext);
    }
}
