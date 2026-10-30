package com.bakr.queryforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory sliding-window rate limiter.
 *
 * <p>Limits: 60 requests per minute per client IP.
 * Returns {@code 429 Too Many Requests} when exceeded.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_REQUESTS = 60;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        if (!bucket.allow()) {
            LOG.warn("rate limit exceeded for key={} path={}", key, request.getRequestURI());
            response.setHeader("Retry-After", "60");
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Rate limit exceeded: max " + MAX_REQUESTS + " requests per minute");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private static final class Bucket {
        private final AtomicLong count = new AtomicLong();
        private long windowStart = System.currentTimeMillis();

        synchronized boolean allow() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= MAX_REQUESTS;
        }
    }
}
