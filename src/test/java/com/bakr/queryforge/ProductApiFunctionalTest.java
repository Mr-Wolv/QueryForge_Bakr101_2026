package com.bakr.queryforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests against a real PostgreSQL.
 *
 * Default mode: spins up a Testcontainers postgres:17 (used on CI and Linux/macOS dev machines).
 * If QF_DB_URL is set, runs against that database instead (useful on Windows hosts where the
 * Docker Desktop named pipes are not reachable from the Java client; point it at the compose db,
 * e.g. jdbc:postgresql://localhost:5433/queryforge).
 *
 * Verifies the API contract: point lookup, filtered search, pagination envelopes,
 * keyset walk correctness (order, no dupes, terminates), and validation errors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductApiFunctionalTest {

    static final boolean USE_EXTERNAL_DB = System.getenv("QF_DB_URL") != null;

    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DB) {
            registry.add("spring.datasource.url", () -> System.getenv("QF_DB_URL"));
            registry.add("spring.datasource.username", () -> "queryforge");
            registry.add("spring.datasource.password", () -> "queryforge");
        } else {
            postgres = new PostgreSQLContainer<>("postgres:17");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper json = new ObjectMapper();

    static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    static final int ROWS = 250;

    @BeforeEach
    void setup() throws Exception {
        // Recreate schema (kept in sync with db/migrations/V1__create_products.sql — baseline: no secondary indexes)
        try (var in = new ClassPathResource("schema/products.sql").getInputStream()) {
            String sql = new String(in.readAllBytes());
            jdbc.execute(sql);
        }
        jdbc.execute("TRUNCATE products RESTART IDENTITY");

        // Deterministic seed: category = i%5+1, status cycle, price spread, created_at strictly increasing
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            String status = switch (i % 3) {
                case 0 -> "ACTIVE";
                case 1 -> "INACTIVE";
                default -> "DISCONTINUED";
            };
            batch.add(new Object[]{
                    "SKU" + i, "Product " + i, (long) (i % 5) + 1,
                    new java.math.BigDecimal(5 + (i * 37) % 496), 10 + i, status,
                    Timestamp.from(BASE.plusSeconds(i)), Timestamp.from(BASE.plusSeconds(i + 1))
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO products (sku, name, category_id, price, stock_quantity, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void pointLookupReturnsRow() {
        String expectedSku = jdbc.queryForObject("SELECT sku FROM products WHERE id = 1", String.class);
        var resp = rest.getForEntity(url("/api/products/1"), JsonNode.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("sku").asText()).isEqualTo(expectedSku);
        assertThat(resp.getBody().get("id").asLong()).isEqualTo(1L);
    }

    @Test
    void pointLookupMissingReturns404() {
        var resp = rest.getForEntity(url("/api/products/99999"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void filteredSearchMatchesDatabaseAndSortsDesc() throws Exception {
        var resp = rest.getForEntity(url(
                "/api/products?category=1&status=ACTIVE&minPrice=100&maxPrice=500&page=0&size=50"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(resp.getBody());

        List<Long> expected = jdbc.queryForList("""
                SELECT id FROM products
                WHERE category_id = 1 AND status = 'ACTIVE' AND price BETWEEN 100 AND 500
                ORDER BY created_at DESC, id DESC
                """, Long.class);

        assertThat(body.get("totalElements").asLong()).isEqualTo(expected.size());
        List<Long> got = new ArrayList<>();
        body.get("content").forEach(n -> got.add(n.get("id").asLong()));
        assertThat(got).isEqualTo(expected.subList(0, Math.min(expected.size(), 50)));
        assertThat(body.get("hasNext").asBoolean()).isEqualTo(expected.size() > 50);
    }

    @Test
    void filteredSearchRejectsBadParameters() {
        assertThat(rest.getForEntity(url("/api/products?size=500"), String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(rest.getForEntity(url("/api/products?sort=bogus"), String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(rest.getForEntity(url("/api/products?minPrice=500&maxPrice=100"), String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(rest.getForEntity(url("/api/products?dir=sideways"), String.class).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void keysetWalkReturnsEverythingInOrderWithoutDuplicates() throws Exception {
        List<Long> expected = jdbc.queryForList("""
                SELECT id FROM products
                WHERE category_id = 1 AND status = 'ACTIVE'
                ORDER BY created_at DESC, id DESC
                """, Long.class);

        Set<Long> seen = new HashSet<>();
        List<Long> walked = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        while (guard++ < 100) {
            String path = "/api/products/keyset?category=1&status=ACTIVE&size=4" + (cursor == null ? "" : "&cursor=" + cursor);
            var resp = rest.getForEntity(URI.create(url(path)), String.class);
            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            JsonNode body = json.readTree(resp.getBody());
            JsonNode content = body.get("content");
            if (content.isEmpty()) {
                break;
            }
            for (JsonNode n : content) {
                long id = n.get("id").asLong();
                assertThat(seen.add(id)).as("no duplicate ids").isTrue();
                walked.add(id);
            }
            cursor = body.path("nextCursor").asText(null);
            if (cursor == null) {
                break;
            }
        }
        assertThat(walked).isEqualTo(expected);
    }

    @Test
    void keysetRejectsMalformedCursor() {
        var resp = rest.getForEntity(url("/api/products/keyset?cursor=@@@not-base64@@@"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void categoriesListed() {
        var resp = rest.getForEntity(url("/api/categories"), JsonNode.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Set<String> names = new HashSet<>();
        resp.getBody().forEach(n -> names.add(n.get("name").asText()));
        assertThat(names).contains("electronics", "clothing", "home", "sports", "books");
    }
}
