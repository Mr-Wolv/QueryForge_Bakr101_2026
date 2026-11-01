package com.bakr.queryforge.experiment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                List.of("idx_pkey"), "indexed", 4, 4096, "2026-01-01T00:00:00Z",
                "k6", "k6 v0.0.0", "script-sha", "configuration-sha");
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
    void captureIncludesRunnerAndStableConfigurationFingerprint() {
        var runner = mock(com.bakr.queryforge.experiment.runner.RunnerAdapter.class);
        when(runner.name()).thenReturn("k6");
        when(runner.version()).thenReturn("2.3.0");
        when(runner.sourceFingerprint("search")).thenReturn("script-sha");

        var first = service.capture("search", Map.of("RATE", "10", "DUR", "2m"), runner);
        var reordered = service.capture("search", Map.of("DUR", "2m", "RATE", "10"), runner);

        assertThat(first.runnerName()).isEqualTo("k6");
        assertThat(first.runnerVersion()).isEqualTo("2.3.0");
        assertThat(first.runnerSourceSha256()).isEqualTo("script-sha");
        assertThat(first.configurationSha256()).matches("[0-9a-f]{64}");
        assertThat(reordered.configurationSha256()).isEqualTo(first.configurationSha256());
        assertThat(service.toMap(first)).containsEntry("runnerVersion", "2.3.0")
                .containsEntry("configurationSha256", first.configurationSha256());
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
