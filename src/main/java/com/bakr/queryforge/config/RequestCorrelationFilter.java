package com.bakr.queryforge.config;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Adds a structured request-correlation ID to every request.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If {@code X-Request-Id} is present, reuse it.</li>
 *   <li>Otherwise generate a new UUID v4.</li>
 *   <li>Echo it back via {@code X-Request-Id} response header.</li>
 *   <li>Bind it to MDC as {@code requestId} for logging.</li>
 * </ul>
 */
@Component
@Order(0)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        response.setHeader(HEADER, requestId);
        try {
            MDC.put(MDC_KEY, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String existing = request.getHeader(HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing.strip();
        }
        return UUID.randomUUID().toString();
    }
}
