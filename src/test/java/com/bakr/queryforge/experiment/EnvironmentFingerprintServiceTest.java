package com.bakr.queryforge.experiment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentFingerprintServiceTest {

    private final EnvironmentFingerprintService service = new EnvironmentFingerprintService();

    @Test
    void appCommitReturnsUnknownWhenEnvNotSet() {
        assertThat(service.appCommit()).isEqualTo("unknown");
    }

    @Test
    void captureWithoutDataSourceReturnsUnavailableSnapshot() {
        EnvironmentFingerprintService.Snapshot snap = service.capture();
        assertThat(snap.available()).isFalse();
        assertThat(snap.databaseProduct()).isEqualTo("unknown");
        assertThat(snap.postgresVersion()).isEqualTo("unknown");
        assertThat(snap.rowCount()).isEqualTo(-1L);
        assertThat(snap.indexes()).isEmpty();
        assertThat(snap.indexState()).isEqualTo("unknown");
        assertThat(snap.appCommit()).isEqualTo("unknown");
        assertThat(snap.checkedAt()).isNotEmpty();
    }

    @Test
    void toMapConvertsSnapshot() {
        EnvironmentFingerprintService.Snapshot snap = new EnvironmentFingerprintService.Snapshot(
                true, "abc-123", "17", "OpenJDK", "Linux", "amd64",
                "PostgreSQL", "17.4", 17, 1_000_000L,
                List.of("idx_pkey"), "indexed", 4, 4096, "2026-01-01T00:00:00Z");
        Map<String, Object> map = service.toMap(snap);
        assertThat(map).containsEntry("available", true);
        assertThat(map).containsEntry("appCommit", "abc-123");
        assertThat(map).containsEntry("databaseProduct", "PostgreSQL");
        assertThat(map).containsEntry("postgresMajorVersion", 17);
        assertThat(map).containsEntry("rowCount", 1_000_000L);
        assertThat(map).containsEntry("indexState", "indexed");
        assertThat(map).containsEntry("cpuCores", 4);
        assertThat(map).containsEntry("memoryMb", 4096L);
    }

    @Test
    void indexStateReturnsUnknownForEmptyList() {
        assertThat(EnvironmentFingerprintService.indexState(List.of())).isEqualTo("unknown");
        assertThat(EnvironmentFingerprintService.indexState(null)).isEqualTo("unknown");
    }

    @Test
    void indexStateReturnsBaselineForOnlyPrimaryKeys() {
        assertThat(EnvironmentFingerprintService.indexState(List.of("products_pkey", "pk_orders")))
                .isEqualTo("baseline");
    }

    @Test
    void indexStateReturnsIndexedForSecondaryIndexes() {
        assertThat(EnvironmentFingerprintService.indexState(List.of("products_pkey", "idx_name")))
                .isEqualTo("indexed");
    }
}
