package com.bakr.queryforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests against a database.
 *
 * Default mode (QF_DB_URL not set): uses the H2 in-memory database configured in
 * src/test/resources/application.properties so tests pass without Docker.
 *
 * If QF_DB_URL is set, runs against that PostgreSQL instead (useful on CI or when
 * pointing at a compose db, e.g. jdbc:postgresql://localhost:5433/queryforge).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.testcontainers.enabled=false")
class ProductApiFunctionalTest {

    static final boolean USE_EXTERNAL_DB = System.getenv("QF_DB_URL") != null;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DB) {
            registry.add("spring.datasource.url", () -> System.getenv("QF_DB_URL"));
            registry.add("spring.datasource.username", () -> "queryforge");
            registry.add("spring.datasource.password", () -> "queryforge");
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        }
        // else: use test application.properties (H2)
    }

    @LocalServerPort
    int port;

    RestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper json = new ObjectMapper();

    static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    static final int ROWS = 250;

    @BeforeEach
    void setup() throws Exception {
        rest = new RestTemplate();
        rest.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
                return false;
            }
        });
        // Drop and recreate schema (H2 in-memory DB persists across test methods via connection pool)
        try (var in = new ClassPathResource("schema/products.sql").getInputStream()) {
            String sql = new String(in.readAllBytes());
            // Wrap each CREATE TABLE with DROP TABLE IF EXISTS for H2 re-runs
            String reset = sql.replaceAll("(?i)CREATE TABLE IF NOT EXISTS", "DROP TABLE IF EXISTS public.__tmp__ CASCADE; CREATE TABLE IF NOT EXISTS");
            // Simpler: just execute the schema as-is (H2 IF NOT EXISTS handles existing tables)
            jdbc.execute(sql);
        }
        jdbc.execute("TRUNCATE TABLE products RESTART IDENTITY");
        jdbc.execute("DELETE FROM categories");
        jdbc.execute("INSERT INTO categories (id, name) VALUES (1,'electronics'),(2,'clothing'),(3,'home'),(4,'sports'),(5,'books')");

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
    void pointLookupReturnsRow() throws Exception {
        String expectedSku = jdbc.queryForObject("SELECT sku FROM products WHERE id = 1", String.class);
        var resp = rest.getForEntity(url("/api/products/1"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(resp.getBody());
        assertThat(body.get("sku").asText()).isEqualTo(expectedSku);
        assertThat(body.get("id").asLong()).isEqualTo(1L);
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
    void keysetRejectsNonCreatedAtSorts() {
        // Cursor encodes (created_at, id): other sort columns have no correctly-typed cursor,
        // so the API contract restricts keyset to sort=createdAt (400, with an explanatory error).
        for (String sort : new String[]{"price", "id"}) {
            var resp = rest.getForEntity(url("/api/products/keyset?sort=" + sort), String.class);
            assertThat(resp.getStatusCode().value()).as("sort=%s must be rejected", sort).isEqualTo(400);
            assertThat(resp.getBody()).as("sort=%s error must explain the restriction", sort)
                    .contains("sort=createdAt");
        }
    }

    @Test
    void keysetAcceptsExplicitCreatedAtSort() {
        // The default is createdAt; making it explicit must stay legal.
        var resp = rest.getForEntity(url("/api/products/keyset?sort=createdAt&dir=desc&size=5"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void unmappedPathReturnsNotFoundProblemDetail() throws Exception {
        var resp = rest.getForEntity(url("/api/definitely-not-a-route"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        JsonNode body = json.readTree(resp.getBody());
        assertThat(body.path("status").asInt()).isEqualTo(404);
        assertThat(body.path("type").asText()).endsWith("not-found");
        assertThat(body.path("instance").asText()).isEqualTo("/api/definitely-not-a-route");
    }

    @Test
    void categoriesListed() throws Exception {
        var resp = rest.getForEntity(url("/api/categories"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(resp.getBody());
        Set<String> names = new HashSet<>();
        body.forEach(n -> names.add(n.get("name").asText()));
        assertThat(names).contains("electronics", "clothing", "home", "sports", "books");
    }
}
