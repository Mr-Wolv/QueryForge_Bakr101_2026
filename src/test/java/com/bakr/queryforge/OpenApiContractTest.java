package com.bakr.queryforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = QueryForgeApplication.class)
class OpenApiContractTest {

    @LocalServerPort
    int port;

    private final RestTemplate rest = new RestTemplate();

    @Test
    void apiDocsExposeExpectedPaths() {
        String url = "http://localhost:" + port + "/v3/api-docs";
        ResponseEntity<String> resp = rest.getForEntity(url, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("/api/runs").contains("/api/comparisons").contains("/api/experiments");
    }
}
