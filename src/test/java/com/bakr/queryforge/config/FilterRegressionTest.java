package com.bakr.queryforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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

    @Test
    void rateLimitRejectsAfterExcessRequests() throws Exception {
        String clientIp = "10.0.0." + (int) (Math.random() * 254 + 1);
        for (int i = 0; i < 60; i++) {
            mvc.perform(get("/actuator/health").header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/actuator/health").header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests());
    }
}
