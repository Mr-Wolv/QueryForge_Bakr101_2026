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

        ReadinessController controller = new ReadinessController(ds, jdbc);
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
        ReadinessController controller = new ReadinessController(ds, jdbc);
        Map<String, Object> body = controller.readiness().getBody();

        Map<?,?> db = (Map<?,?>) body.get("database");
        assertThat(db.get("status")).isEqualTo("DOWN");
        assertThat(db.get("version")).isEqualTo("unknown");
    }
}
