package com.bakr.queryforge.experiment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the environment facts that make a measurement reproducible.
 *
 * <p>The fingerprint is stored in every manifest and compared before two runs
 * are declared compatible. Capture is best-effort: a missing database must not
 * prevent the API from starting, but the resulting manifest is explicitly
 * marked unavailable instead of silently pretending the values were observed.
 */
@Service
public class EnvironmentFingerprintService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public EnvironmentFingerprintService(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    /** Test/unit constructor with no database. */
    public EnvironmentFingerprintService() {
        this(null, null);
    }

    /** Commit identifier recorded in manifests without touching the database. */
    public String appCommit() {
        return envOrDefault("GIT_COMMIT", "unknown");
    }

    public Snapshot capture() {
        String postgresVersion = "unknown";
        int postgresMajorVersion = -1;
        String databaseProduct = "unknown";
        long rowCount = -1;
        List<String> indexes = List.of();
        boolean available = false;

        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                available = conn.isValid(2);
                var meta = conn.getMetaData();
                postgresVersion = meta.getDatabaseProductVersion();
                postgresMajorVersion = meta.getDatabaseMajorVersion();
                databaseProduct = meta.getDatabaseProductName();
            } catch (Exception ignored) {
                available = false;
            }
        }
        if (available && jdbc != null) {
            try {
                Long count = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
                rowCount = count == null ? -1 : count;
            } catch (Exception ignored) {
                rowCount = -1;
            }
            try {
                indexes = new ArrayList<>(jdbc.queryForList(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY indexname",
                        String.class));
            } catch (Exception ignored) {
                indexes = List.of();
            }
        }

        String indexState = indexState(indexes);
        return new Snapshot(
                available,
                appCommit(),
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                databaseProduct,
                postgresVersion,
                postgresMajorVersion,
                rowCount,
                List.copyOf(indexes),
                indexState,
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / 1024 / 1024,
                Instant.now().toString()
        );
    }

    public Map<String, Object> toMap(Snapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("available", snapshot.available());
        map.put("appCommit", snapshot.appCommit());
        map.put("javaVersion", snapshot.javaVersion());
        map.put("javaVendor", snapshot.javaVendor());
        map.put("os", snapshot.os());
        map.put("osArch", snapshot.osArch());
        map.put("databaseProduct", snapshot.databaseProduct());
        map.put("postgresVersion", snapshot.postgresVersion());
        map.put("postgresMajorVersion", snapshot.postgresMajorVersion());
        map.put("rowCount", snapshot.rowCount());
        map.put("indexes", snapshot.indexes());
        map.put("indexState", snapshot.indexState());
        map.put("cpuCores", snapshot.cpuCores());
        map.put("memoryMb", snapshot.memoryMb());
        map.put("checkedAt", snapshot.checkedAt());
        return map;
    }

    public static String indexState(List<String> indexes) {
        if (indexes == null || indexes.isEmpty()) return "unknown";
        boolean hasSecondary = indexes.stream().anyMatch(name -> {
            String lower = name.toLowerCase();
            return !lower.endsWith("_pkey") && !lower.startsWith("pk_");
        });
        return hasSecondary ? "indexed" : "baseline";
    }

    private static String envOrDefault(String key, String def) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? def : value;
    }

    public record Snapshot(
            boolean available,
            String appCommit,
            String javaVersion,
            String javaVendor,
            String os,
            String osArch,
            String databaseProduct,
            String postgresVersion,
            int postgresMajorVersion,
            long rowCount,
            List<String> indexes,
            String indexState,
            int cpuCores,
            long memoryMb,
            String checkedAt
    ) {}
}
