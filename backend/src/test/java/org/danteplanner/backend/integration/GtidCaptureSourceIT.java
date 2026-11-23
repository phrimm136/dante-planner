package org.danteplanner.backend.integration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("containerized")
@ResourceLock(CausalHarnessSupport.SHARED_HARNESS)
class GtidCaptureSourceIT {

    private final GtidWriteCapture capture = new GtidWriteCapture(new SimpleMeterRegistry());

    @BeforeEach
    void createTable() throws SQLException {
        execute("CREATE TABLE capture_probe (id INT PRIMARY KEY AUTO_INCREMENT)");
        capture.begin();
    }

    @AfterEach
    void dropTable() throws SQLException {
        capture.clear();
        execute("DROP TABLE capture_probe");
    }

    @Test
    void commit_WhenTheDriverDoesNotTrackSessionState_CapturesTheSuperset() throws SQLException {
        insert(CausalHarnessSupport.PRIMARY.getJdbcUrl());

        assertThat(capture.takeCapture()).hasValueSatisfying(taken -> assertThat(taken.tracked()).isFalse());
    }

    @Test
    void commit_WhenDriverAndServerTrack_CapturesTheTransactionsOwnGtid() throws SQLException {
        insert(trackingUrl());

        assertThat(capture.takeCapture()).hasValueSatisfying(taken -> assertThat(taken.tracked()).isTrue());
    }

    @Test
    void commit_WhenDriverAndServerTrackAndNothingIsWritten_CapturesNothing() throws SQLException {
        commit(trackingUrl(), "SELECT 1");

        assertThat(capture.takeCapture()).isEmpty();
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = driver(CausalHarnessSupport.PRIMARY.getJdbcUrl()).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void insert(String url) throws SQLException {
        commit(url, "INSERT INTO capture_probe () VALUES ()");
    }

    private void commit(String url, String sql) throws SQLException {
        try (Connection connection = new GtidCapturingDataSource(driver(url), capture).getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            connection.commit();
        }
    }

    private static String trackingUrl() {
        return CausalHarnessSupport.withSessionStateTracking(CausalHarnessSupport.PRIMARY.getJdbcUrl());
    }

    private static DriverManagerDataSource driver(String url) {
        return new DriverManagerDataSource(url, CausalHarnessSupport.PRIMARY.getUsername(),
                CausalHarnessSupport.PRIMARY.getPassword());
    }
}
