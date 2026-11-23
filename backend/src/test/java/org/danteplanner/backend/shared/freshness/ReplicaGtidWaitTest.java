package org.danteplanner.backend.shared.freshness;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import ch.qos.logback.classic.Level;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplicaGtidWaitTest {

    private static final String GTID = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-5";
    private static final String REPLICA = "seoul-replica";
    private static final Duration BOUND = Duration.ofMillis(250);
    private static final double BOUND_SECONDS = 0.25;
    private static final int WAIT_APPLIED = 0;
    private static final int WAIT_TIMED_OUT = 1;

    @Mock
    private DataSource replica;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet result;

    @Mock
    private ResultSetMetaData metaData;

    private ReadSourceSelector.Source source;

    @BeforeEach
    void setUp() {
        source = new ReadSourceSelector.Source(REPLICA, false, replica, CircuitBreaker.ofDefaults(REPLICA));
    }

    private void answers(int value) throws SQLException {
        when(replica.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(result.next()).thenReturn(true, false);
        when(result.getInt(1)).thenReturn(value);
    }

    @Test
    void isApplied_WhenTheReplicaAppliedTheSet_IsTrueAfterWaitingAtMostTheBoundInSeconds() throws SQLException {
        answers(WAIT_APPLIED);

        assertThat(ReplicaGtidWait.isApplied(source, GTID, BOUND)).isTrue();

        verify(connection).prepareStatement("SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)");
        verify(statement).setString(1, GTID);
        verify(statement).setObject(2, BOUND_SECONDS);
    }

    @Test
    void isApplied_WhenTheWaitTimesOut_IsFalse() throws SQLException {
        answers(WAIT_TIMED_OUT);

        assertThat(ReplicaGtidWait.isApplied(source, GTID, BOUND)).isFalse();
    }

    @Test
    void isApplied_WhenTheReplicaCannotBeAsked_IsFalseAndWarnsOnce() throws SQLException {
        when(replica.getConnection()).thenThrow(new SQLException("Communications link failure", "08S01"));

        try (LogCapture logs = LogCapture.onThisThread(ReplicaGtidWait.class)) {
            assertThat(ReplicaGtidWait.isApplied(source, GTID, BOUND)).isFalse();
            assertThat(logs.at(Level.WARN)).hasSize(1);
        }
    }
}
