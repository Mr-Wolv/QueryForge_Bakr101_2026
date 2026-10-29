package com.bakr.queryforge.experiment;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/lab")
public class ReadinessController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public ReadinessController(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean dbOk = false;
        String dbVersion = null;
        long rowCount = -1;
        try (Connection conn = dataSource.getConnection()) {
            dbOk = conn.isValid(2);
            dbVersion = conn.getMetaData().getDatabaseProductVersion();
            rowCount = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        } catch (Exception e) {
            // leave fields as not-ready
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api", Map.of("status", "UP"));
        body.put("database", Map.of(
                "status", dbOk ? "UP" : "DOWN",
                "version", dbVersion == null ? "unknown" : dbVersion,
                "rowCount", rowCount
        ));
        body.put("k6", Map.of(
                "status", k6Available() ? "UP" : "UNKNOWN",
                "path", System.getenv("K6_PATH") == null ? "tools/k6/k6.exe" : System.getenv("K6_PATH")
        ));
        body.put("fingerprint", Map.of(
                "appCommit", envOrDefault("GIT_COMMIT", "unknown"),
                "javaVersion", System.getProperty("java.version"),
                "postgresVersion", dbVersion == null ? "unknown" : dbVersion,
                "checkedAt", Instant.now().toString()
        ));

        return ResponseEntity.ok(body);
    }

    private static boolean k6Available() {
        String path = System.getenv("K6_PATH");
        if (path == null) path = "tools/k6/k6.exe";
        Path p = Path.of(path);
        if (!p.isAbsolute()) p = Path.of(System.getProperty("user.dir")).resolve(path);
        return java.nio.file.Files.isRegularFile(p) || "k6".equals(path);
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
