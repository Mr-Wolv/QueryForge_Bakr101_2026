package com.bakr.queryforge.experiment;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/lab")
public class ReadinessController {

    private static final Logger LOG = LoggerFactory.getLogger(ReadinessController.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final DatasetInitializer datasetInitializer;

    public ReadinessController(DataSource dataSource, JdbcTemplate jdbc, DatasetInitializer datasetInitializer) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.datasetInitializer = datasetInitializer;
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
                "path", k6Path()
        ));
        body.put("fingerprint", Map.of(
                "appCommit", envOrDefault("GIT_COMMIT", "unknown"),
                "javaVersion", System.getProperty("java.version"),
                "postgresVersion", dbVersion == null ? "unknown" : dbVersion,
                "checkedAt", Instant.now().toString()
        ));

        return ResponseEntity.ok(body);
    }

    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initialize(@RequestParam(name = "rows", defaultValue = "1000000") int rows) {
        if (rows <= 0 || rows > 10_000_000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "rows must be between 1 and 10000000");
        }

        DatasetInitializer.InitResult result = datasetInitializer.initialize(rows);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.alreadyExists() ? "already_exists" : "initialized");
        body.put("rowCount", result.rowCount());
        body.put("elapsedMs", result.elapsedMs());
        body.put("message", result.message());

        return ResponseEntity.ok(body);
    }

    private static boolean k6Available() {
        String configured = configuredK6Path();
        if (configured != null) {
            return isExecutableAvailable(configured, System.getenv("PATH"), System.getenv("PATHEXT"),
                    System.getProperty("user.dir"));
        }

        Path bundled = bundledK6Path();
        if (java.nio.file.Files.isRegularFile(bundled)) return true;
        return isExecutableAvailable("k6", System.getenv("PATH"), System.getenv("PATHEXT"),
                System.getProperty("user.dir"));
    }

    private static String k6Path() {
        String configured = configuredK6Path();
        if (configured != null) return configured;
        Path bundled = bundledK6Path();
        return java.nio.file.Files.isRegularFile(bundled) ? bundled.toString() : "k6 (PATH)";
    }

    private static String configuredK6Path() {
        String path = System.getenv("K6_BIN");
        if (path == null || path.isBlank()) path = System.getenv("K6_PATH");
        return path == null || path.isBlank() ? null : path;
    }

    private static Path bundledK6Path() {
        String executable = isWindows() ? "k6.exe" : "k6";
        return Path.of("tools", "k6", executable);
    }

    static boolean isExecutableAvailable(String executable, String path, String pathExt, String userDir) {
        Path executablePath;
        try {
            executablePath = Path.of(executable);
        } catch (java.nio.file.InvalidPathException e) {
            LOG.warn("Configured k6 executable path is invalid");
            return false;
        }

        if (executablePath.isAbsolute() || executablePath.getNameCount() > 1) {
            if (!executablePath.isAbsolute()) executablePath = Path.of(userDir).resolve(executablePath);
            return isExecutableFile(executablePath);
        }
        if (path == null || path.isBlank()) return false;

        List<String> names = executableNames(executablePath.toString(), pathExt);
        for (String directory : path.split(Pattern.quote(java.io.File.pathSeparator))) {
            if (directory.isBlank()) continue;
            Path directoryPath = Path.of(directory);
            if (!directoryPath.isAbsolute()) directoryPath = Path.of(userDir).resolve(directoryPath);
            for (String name : names) {
                if (isExecutableFile(directoryPath.resolve(name))) return true;
            }
        }
        return false;
    }

    private static List<String> executableNames(String executable, String pathExt) {
        if (!isWindows()) return List.of(executable);
        String extensions = pathExt == null || pathExt.isBlank() ? ".COM;.EXE;.BAT;.CMD" : pathExt;
        List<String> names = new ArrayList<>();
        names.add(executable);
        for (String extension : extensions.split(";")) {
            if (!extension.isBlank() && !executable.toLowerCase().endsWith(extension.toLowerCase())) {
                names.add(executable + extension);
            }
        }
        return names;
    }

    private static boolean isExecutableFile(Path path) {
        return java.nio.file.Files.isRegularFile(path)
                && (isWindows() || java.nio.file.Files.isExecutable(path));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
