package com.bakr.queryforge.service;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) {
}
