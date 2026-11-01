package com.bakr.queryforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.bakr.queryforge.artifact.RunArtifact;
import com.bakr.queryforge.experiment.ExperimentRun;
import com.bakr.queryforge.experiment.ExperimentRunState;
import com.bakr.queryforge.experiment.RunPersistenceService;
import com.bakr.queryforge.measurement.Metrics;
import com.bakr.queryforge.repo.ExperimentRunRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the application against a real PostgreSQL 17 instance.
 *
 * <p>The product schema is initialized from the operator's V1 SQL before
 * Flyway baselines at version 2 and applies only the run-store migrations.
 * The test does not depend on a locally running compose database. When Docker
 * is unavailable the class is skipped instead of failing the unit suite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresIntegrationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(
                    "postgres:17-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b")
                    .asCompatibleSubstituteFor("postgres"))
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("db", "migrations", "V1__create_products.sql")
                            .toAbsolutePath().normalize()),
                    "/docker-entrypoint-initdb.d/02-schema.sql");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.locations",
                () -> "filesystem:" + Path.of("db", "flyway").toAbsolutePath().normalize());
        registry.add("spring.flyway.baseline-on-migrate", () -> true);
        registry.add("spring.flyway.baseline-version", () -> "2");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RunPersistenceService runPersistence;

    @Autowired
    ExperimentRunRepository runRepository;

    @Test
    void schemaAndReferenceDataLoadOnRealPostgres() {
        assertThat(jdbc.queryForObject("select count(*) from categories", Long.class)).isEqualTo(5);
    }

    @Test
    void flywayBaselinesProductSchemaAndLeavesManualIndexUnapplied() {
        assertThat(jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '2' and type = 'BASELINE'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where version in ('3', '4') and type = 'SQL' and success
                """, Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_indexes where indexname = 'idx_products_search'
                """, Long.class)).isZero();
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

    @Test
    void runStorePaginatesFilteredResultsAndPersistsArtifacts() {
        Instant start = Instant.parse("2026-10-30T00:00:00Z");
        ExperimentRun first = run("integration-run-a", "SUCCEEDED", "orders", start);
        ExperimentRun second = run("integration-run-b", "SUCCEEDED", "orders", start.plusSeconds(60));
        ExperimentRun excluded = run("integration-run-c", "FAILED", "payments", start.plusSeconds(120));

        runPersistence.importLegacyRuns(List.of(first, second, excluded));
        assertThat(runPersistence.importLegacyRuns(List.of(first, second, excluded))).isZero();

        var page = runPersistence.findPage(
                "succeeded", "orders", start, start.plusSeconds(120),
                0, 1, "startedAt", "desc");
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).singleElement().satisfies(run -> {
            assertThat(run.id()).isEqualTo(second.id());
            assertThat(run.artifacts()).singleElement().satisfies(artifact ->
                    assertThat(artifact.artifactId()).isEqualTo("summary"));
        });
    }

    @Test
    void runStoreRejectsStaleConcurrentUpdates() {
        ExperimentRun initial = run(
                "integration-run-version", "QUEUED", "orders", Instant.now());
        runPersistence.save(initial);
        var stale = runRepository.findById(initial.id()).orElseThrow();
        ExperimentRun updated = new ExperimentRun(
                initial.id(), initial.templateId(), "RUNNING", initial.startedAt(),
                initial.durationMs(), initial.labels(), initial.parameters(),
                initial.metrics(), initial.artifacts(), initial.appCommit());

        runPersistence.save(updated);

        assertThatThrownBy(() -> runRepository.saveAndFlush(stale))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    private static ExperimentRun run(String id, String state, String templateId, Instant startedAt) {
        return new ExperimentRun(
                id,
                templateId,
                state,
                startedAt,
                25,
                Map.of("legacy", "true"),
                Map.of("RATE", "10"),
                Metrics.empty(),
                List.of(new RunArtifact(
                        "summary", "k6-summary", "application/json",
                        "benchmark/runs/" + id + "/summary.json", "summary")),
                "integration-commit");
    }
}
