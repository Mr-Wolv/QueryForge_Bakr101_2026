package com.bakr.queryforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = QueryForgeApplication.class)
@ActiveProfiles("postgres")
class OpenApiContractTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void apiDocsExposeExpectedPaths() {
        ResponseEntity<String> resp = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("/api/runs").contains("/api/comparisons").contains("/api/experiments");
    }
}
