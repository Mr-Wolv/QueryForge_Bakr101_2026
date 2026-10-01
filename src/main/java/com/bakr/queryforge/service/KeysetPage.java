package com.bakr.queryforge.service;

import java.util.List;

public record KeysetPage<T>(List<T> content, String nextCursor) {
}
