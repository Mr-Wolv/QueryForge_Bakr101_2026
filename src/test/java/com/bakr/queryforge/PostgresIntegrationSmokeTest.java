package com.bakr.queryforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the application against a real PostgreSQL 17 instance.
 *
 * <p>The schema comes from the same migration file the operator applies
 * ({@code db/migrations/V1__create_products.sql}); the test does not depend on
 * a locally running compose database. When Docker is unavailable the class is
 * skipped instead of failing the unit suite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresIntegrationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "file:db/migrations/V1__create_products.sql");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void schemaAndReferenceDataLoadOnRealPostgres() {
        assertThat(jdbc.queryForObject("select count(*) from categories", Long.class)).isEqualTo(5);
    }

    @Test
    void filteredSearchWorkloadRunsAgainstPostgres() {
        jdbc.update("""
                insert into products (sku, name, category_id, price, stock_quantity, status)
                values (?, ?, ?, ?, ?, ?)
                """, "qf-integration-1", "Integration product", 1,
                new BigDecimal("19.99"), 3, "ACTIVE");

        Long matches = jdbc.queryForObject("""
                select count(*) from products
                where category_id = ? and status = ? and price between ? and ?
                """, Long.class, 1, "ACTIVE",
                new BigDecimal("10.00"), new BigDecimal("20.00"));

        assertThat(matches).isEqualTo(1);
    }
}
