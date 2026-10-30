package com.bakr.queryforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the throttle.
 *
 * <p>The original implementation called {@code response.sendError(429)}, which re-enters the
 * container's ERROR dispatch and surfaced to API clients as {@code 500 Internal Server Error}.
 * That turned a throttled request into an unexplained server fault, which is how the E2E suite
 * kept seeing empty pages: a browser, k6 and Playwright all share one client IP locally, so the
 * budget ran out mid-run.
 */
class RateLimitFilterTest {

    private static final String PROBLEM = "application/problem+json";

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr("127.0.0.1");
        return req;
    }

    @Test
    void passesRequestsUnderTheLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, true);
        AtomicInteger passed = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/runs"), response, new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                    passed.incrementAndGet();
                }
            });
            assertThat(response.getStatus()).isEqualTo(200);
        }

        assertThat(passed).hasValue(3);
    }

    @Test
    void throttlesOverTheLimitWithAProblem429NotA500() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, true);
        AtomicInteger passed = new AtomicInteger();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                passed.incrementAndGet();
            }
        };

        filter.doFilter(request("/api/runs"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("/api/runs"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse throttled = new MockHttpServletResponse();
        filter.doFilter(request("/api/runs"), throttled, chain);

        assertThat(passed).as("third request must not reach the chain").hasValue(2);
        assertThat(throttled.getStatus())
                .as("a throttled request must be 429, not a 500 from the ERROR dispatch")
                .isEqualTo(429);
        assertThat(throttled.getHeader("Retry-After")).isEqualTo("60");
        assertThat(throttled.getContentType()).startsWith(PROBLEM);
        assertThat(throttled.getContentAsString())
                .contains("https://problem.org/too-many-requests")
                .contains("Rate limit exceeded")
                .contains("\"status\":429");
    }

    @Test
    void healthAndErrorPathsNeverConsumeTheBudget() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, true);
        AtomicInteger passed = new AtomicInteger();
        MockFilterChain counting = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                passed.incrementAndGet();
            }
        };

        // Exhaust the budget on a normal API path.
        filter.doFilter(request("/api/runs"), new MockHttpServletResponse(), counting);
        filter.doFilter(request("/api/runs"), new MockHttpServletResponse(), counting);
        assertThat(passed).hasValue(1);

        // Health checks and the error page still get through.
        for (String uri : new String[]{"/actuator/health", "/error"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(uri), response, counting);
            assertThat(response.getStatus()).as(uri).isEqualTo(200);
        }
        assertThat(passed).hasValue(3);
    }

    @Test
    void disabledFilterNeverThrottles() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, false);
        AtomicInteger passed = new AtomicInteger();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                passed.incrementAndGet();
            }
        };

        for (int i = 0; i < 5; i++) {
            filter.doFilter(request("/api/runs"), new MockHttpServletResponse(), chain);
        }

        assertThat(passed).hasValue(5);
    }

    @Test
    void distinctClientsGetSeparateBudgets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, true);
        AtomicInteger passed = new AtomicInteger();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                passed.incrementAndGet();
            }
        };

        MockHttpServletRequest local = new MockHttpServletRequest("GET", "/api/runs");
        local.setRemoteAddr("127.0.0.1");
        MockHttpServletRequest forwarded = new MockHttpServletRequest("GET", "/api/runs");
        forwarded.setRemoteAddr("10.0.0.5");
        forwarded.addHeader("X-Forwarded-For", "203.0.113.9");

        filter.doFilter(local, new MockHttpServletResponse(), chain);
        filter.doFilter(forwarded, new MockHttpServletResponse(), chain);

        assertThat(passed).hasValue(2);
    }
}
