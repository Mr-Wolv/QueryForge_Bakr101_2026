package com.bakr.queryforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory sliding-window rate limiter.
 *
 * <p>Limits: {@code qf.rate-limit.requests-per-minute} requests per minute per client IP
 * (default 600, {@code qf.rate-limit.enabled=false} turns it off). A local lab serves the
 * browser, k6 and the test-suite from one address, so the budget is sized for a full page
 * load plus a test run rather than for a single client.
 *
 * <p>The throttle writes the RFC 7807 body directly instead of calling
 * {@code response.sendError()}: the container's ERROR dispatch re-enters the dispatcher and
 * surfaces as {@code 500 Internal Server Error}, which hides the real {@code 429} from API
 * clients.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String PROBLEM_TYPE = "https://problem.org/too-many-requests";
    private static final long WINDOW_MS = 60_000;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

    private final int maxRequests;
    private final boolean enabled;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${qf.rate-limit.requests-per-minute:600}") int maxRequests,
            @Value("${qf.rate-limit.enabled:true}") boolean enabled) {
        this.maxRequests = maxRequests;
        this.enabled = enabled;
        LOG.info("rate limit configured: {} requests/minute, enabled={}", maxRequests, enabled);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String uri = request.getRequestURI();
        // Health checks and the error page must never consume the budget.
        return uri == null || uri.startsWith("/actuator") || uri.startsWith("/error");
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        // The container's ERROR dispatch must never be throttled.
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        if (!bucket.allow()) {
            LOG.warn("rate limit exceeded for key={} path={} limit={}/min", key, request.getRequestURI(), maxRequests);
            writeThrottled(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeThrottled(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded: max " + maxRequests + " requests per minute");
        problem.setType(java.net.URI.create(PROBLEM_TYPE));
        problem.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        if (request.getRequestURI() != null) {
            problem.setInstance(java.net.URI.create(request.getRequestURI()));
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(WINDOW_MS / 1000));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(problem));
    }

    private static String clientKey(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    /** Inner (non-static) class so a bucket reads the configured limit of its filter. */
    private final class Bucket {
        private final AtomicLong count = new AtomicLong();
        private long windowStart = System.currentTimeMillis();

        synchronized boolean allow() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }
}
