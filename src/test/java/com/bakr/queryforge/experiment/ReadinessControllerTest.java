package com.bakr.queryforge.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessControllerTest {

    @Test
    void detectsK6InstalledOnPath(@TempDir Path directory) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path binary = Files.createFile(directory.resolve(windows ? "k6.exe" : "k6"));
        if (!windows) binary.toFile().setExecutable(true);

        assertThat(ReadinessController.isExecutableAvailable("k6", directory.toString(), ".EXE",
                directory.toString())).isTrue();
        assertThat(ReadinessController.isExecutableAvailable("missing-k6", directory.toString(), ".EXE",
                directory.toString())).isFalse();
    }

    @Test
    void readinessReportsUpWhenDatabaseIsReachable() throws Exception {
        DataSource ds = Mockito.mock(DataSource.class);
        Connection conn = Mockito.mock(Connection.class);
        java.sql.DatabaseMetaData meta = Mockito.mock(java.sql.DatabaseMetaData.class);

        org.springframework.jdbc.core.JdbcTemplate jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        Mockito.when(jdbc.queryForObject(Mockito.eq("SELECT count(*) FROM products"), Mockito.eq(Long.class)))
                .thenReturn(1000L);

        Mockito.when(ds.getConnection()).thenReturn(conn);
        Mockito.when(conn.isValid(2)).thenReturn(true);
        Mockito.when(conn.getMetaData()).thenReturn(meta);
        Mockito.when(meta.getDatabaseProductVersion()).thenReturn("17.5");

        ReadinessController controller = new ReadinessController(ds, jdbc, Mockito.mock(DatasetInitializer.class));
        Map<String, Object> body = controller.readiness().getBody();

        assertThat(body).containsKey("api").containsKey("database").containsKey("k6").containsKey("fingerprint");
        Map<?,?> db = (Map<?,?>) body.get("database");
        assertThat(db.get("status")).isEqualTo("UP");
        assertThat(db.get("version")).isEqualTo("17.5");
        assertThat(((Number) db.get("rowCount")).longValue()).isEqualTo(1000);
    }

    @Test
    void readinessReportsDownWhenDatabaseFails() throws Exception {
        DataSource ds = Mockito.mock(DataSource.class);
        Mockito.when(ds.getConnection()).thenThrow(new SQLException("connection refused"));

        org.springframework.jdbc.core.JdbcTemplate jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        ReadinessController controller = new ReadinessController(ds, jdbc, Mockito.mock(DatasetInitializer.class));
        Map<String, Object> body = controller.readiness().getBody();

        Map<?,?> db = (Map<?,?>) body.get("database");
        assertThat(db.get("status")).isEqualTo("DOWN");
        assertThat(db.get("version")).isEqualTo("unknown");
    }

    @Test
    void initializeReturnsAlreadyExistsWhenDataPresent() throws Exception {
        DataSource ds = Mockito.mock(DataSource.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);

        DatasetInitializer.InitResult alreadyExists = new DatasetInitializer.InitResult(true, 250L, -1L, "Dataset already contains 250 rows.");
        DatasetInitializer initializer = Mockito.mock(DatasetInitializer.class);
        Mockito.when(initializer.initialize(10000)).thenReturn(alreadyExists);

        ReadinessController controller = new ReadinessController(ds, jdbc, initializer);
        Map<String, Object> body = controller.initialize(10000).getBody();

        assertThat(body.get("status")).isEqualTo("already_exists");
        assertThat(((Number) body.get("rowCount")).longValue()).isEqualTo(250);
    }

    @Test
    void initializeRejectsInvalidRowCount() {
        DataSource ds = Mockito.mock(DataSource.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        DatasetInitializer initializer = Mockito.mock(DatasetInitializer.class);
        ReadinessController controller = new ReadinessController(ds, jdbc, initializer);

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.initialize(0));

        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void initializeSeedsDatasetWhenEmpty() throws Exception {
        DataSource ds = Mockito.mock(DataSource.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        Mockito.when(jdbc.queryForObject(Mockito.eq("SELECT count(*) FROM products"), Mockito.eq(Long.class)))
                .thenReturn(0L, 1000000L);

        DatasetInitializer.InitResult result = new DatasetInitializer.InitResult(false, 1_000_000, 1234L, "Initialized 1000000 rows in 1234 ms.");
        DatasetInitializer initializer = Mockito.mock(DatasetInitializer.class);
        Mockito.when(initializer.initialize(1000000)).thenReturn(result);

        ReadinessController controller = new ReadinessController(ds, jdbc, initializer);
        Map<String, Object> body = controller.initialize(1000000).getBody();

        assertThat(body.get("status")).isEqualTo("initialized");
        assertThat(((Number) body.get("rowCount")).longValue()).isEqualTo(1_000_000);
        assertThat(((Number) body.get("elapsedMs")).longValue()).isEqualTo(1234);
    }
}
