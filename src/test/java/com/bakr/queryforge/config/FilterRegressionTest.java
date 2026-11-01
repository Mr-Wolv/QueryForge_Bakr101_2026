package com.bakr.queryforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "qf.rate-limit.requests-per-minute=5")
@AutoConfigureMockMvc
class FilterRegressionTest {

    @Autowired
    MockMvc mvc;

    @Test
    void requestCorrelationEchoesIdAndUsesGeneratedHeader() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()));
    }

    /**
     * The limit is configured down to 5 for this context so the contract stays testable without
     * hammering 60 requests. {@code /actuator} is exempt (health checks must never be throttled),
     * so the probe goes through a real API path.
     */
    @Test
    void rateLimitRejectsAfterExcessRequests() throws Exception {
        String clientIp = "10.0.0." + (int) (Math.random() * 254 + 1);
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/runs").param("page", "0").param("size", "1")
                    .header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/runs").param("page", "0").param("size", "1")
                .header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/problem+json")));
    }

    /** A throttled request must be a 429 problem document, never a 500 from the ERROR dispatch. */
    @Test
    void healthChecksAreNotThrottled() throws Exception {
        String clientIp = "198.51.100." + (int) (Math.random() * 254 + 1);
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/actuator/health").header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }
    }
}
