package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.config.ReplicaDataSourceProperties;

class GtidPinExitTest {

    private static final int READS = 20;
    private static final String MALFORMED = "not-a-gtid";
    private static final String FOREIGN_UUID = "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";
    private static final String OWN_AND_FOREIGN = "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50, "
            + "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";
    private static final String WAIT_FUNCTION = "WAIT_FOR_EXECUTED_GTID_SET";
    private static final String SUBSET_FUNCTION = "GTID_SUBSET";
    private static final int WAIT_APPLIED = 0;
    private static final int WAIT_TIMED_OUT = 1;
    private static final int NOT_SUBSET = 0;
    private static final int SUBSET = 1;

    private final Logger gateLogger = (Logger) LoggerFactory.getLogger(GtidReadGate.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final PreparedStatement waitStatement = mock(PreparedStatement.class);
    private final PreparedStatement subsetStatement = mock(PreparedStatement.class);
    private final List<String> preparedSql = new ArrayList<>();

    @AfterEach
    void detach() {
        gateLogger.detachAppender(appender);
    }

    private static String rawCookie(String gtid) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(gtid.getBytes(StandardCharsets.UTF_8));
    }

    private static Answer<ResultSet> returning(int value) {
        return invocation -> {
            ResultSet resultSet = mock(ResultSet.class);
            ResultSetMetaData metaData = mock(ResultSetMetaData.class);
            when(metaData.getColumnCount()).thenReturn(1);
            when(resultSet.getMetaData()).thenReturn(metaData);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getInt(1)).thenReturn(value);
            return resultSet;
        };
    }

    private static Answer<ResultSet> throwing(SQLException failure) {
        return invocation -> {
            throw failure;
        };
    }

    /** MySQL 8.0.46 answers a malformed set with ERROR 1772 (HY000). */
    private static SQLException malformedGtidSet(String gtid) {
        return new SQLException("Malformed GTID set specification '" + gtid + "'.", "HY000", 1772);
    }

    private static SQLException connectionFailure() {
        return new SQLException("Communications link failure", "08S01", 0);
    }

    private DataSource database(Answer<ResultSet> waitAnswer, Answer<ResultSet> subsetAnswer)
            throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            preparedSql.add(sql);
            return sql.contains(SUBSET_FUNCTION) ? subsetStatement : waitStatement;
        });
        when(waitStatement.executeQuery()).thenAnswer(waitAnswer);
        when(subsetStatement.executeQuery()).thenAnswer(subsetAnswer);
        return dataSource;
    }

    private GtidCookieFilter filterOver(DataSource dataSource) {
        appender.start();
        gateLogger.addAppender(appender);
        return new GtidCookieFilter(
                new GtidReadGate(dataSource, meters), new GtidWriteCapture(meters), true);
    }

    /** Sends the cookie until a response clears it, as a browser does. */
    private List<MockHttpServletResponse> consecutiveReads(GtidCookieFilter filter, String gtid)
            throws Exception {
        List<MockHttpServletResponse> responses = new ArrayList<>();
        boolean held = true;
        for (int i = 0; i < READS; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
            if (held) {
                request.setCookies(new Cookie(GtidCookie.NAME, rawCookie(gtid)));
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            responses.add(response);
            held = held && !clearsCookie(response);
        }
        return responses;
    }

    private static boolean clearsCookie(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(h -> h.startsWith(GtidCookie.NAME + "=") && h.contains("Max-Age=0"));
    }

    private List<ILoggingEvent> warnings() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    private double primaryPinned() {
        return meters.counter("gtid.gate", "outcome", "primary").count();
    }

    private long subsetQueries() {
        return preparedSql.stream().filter(sql -> sql.contains(SUBSET_FUNCTION)).count();
    }

    private long waitQueries() {
        return preparedSql.stream().filter(sql -> sql.contains(WAIT_FUNCTION)).count();
    }

    @Test
    void readGet_WhenMalformedGtidCookie_FirstResponseClearsAndNothingIsPinned() throws Exception {
        GtidCookieFilter filter = filterOver(database(
                throwing(malformedGtidSet(MALFORMED)), returning(SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, MALFORMED);

        assertThat(clearsCookie(responses.get(0))).as("first response clears").isTrue();
        assertThat(primaryPinned()).isZero();
        assertThat(waitQueries()).isZero();
        assertThat(warnings()).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void readGet_WhenReplicaRejectsGtidAsMalformed_ClearsUnpinnedAndWarnsOnceWithoutStack()
            throws Exception {
        GtidCookieFilter filter = filterOver(database(
                throwing(malformedGtidSet(FOREIGN_UUID)), returning(SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(clearsCookie(responses.get(0))).as("first response clears").isTrue();
        assertThat(primaryPinned()).isZero();
        assertThat(subsetQueries()).isZero();
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0).getThrowableProxy()).isNull();
    }

    @Test
    void readGet_WhenPrimaryNeverExecutedGtid_FirstReadPinnedAndClearsThenUnpinned()
            throws Exception {
        GtidCookieFilter filter = filterOver(database(
                returning(WAIT_TIMED_OUT), returning(NOT_SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(clearsCookie(responses.get(0))).as("first response clears").isTrue();
        assertThat(primaryPinned()).as("only the first read pins").isEqualTo(1);
        assertThat(subsetQueries()).isEqualTo(1);
        verify(subsetStatement).setString(1, FOREIGN_UUID);
    }

    @Test
    void readGet_WhenOneOfSeveralUuidGroupsIsForeign_ClearsAfterAskingPrimaryForWholeSet()
            throws Exception {
        GtidCookieFilter filter = filterOver(database(
                returning(WAIT_TIMED_OUT), returning(NOT_SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, OWN_AND_FOREIGN);

        assertThat(clearsCookie(responses.get(0))).isTrue();
        assertThat(primaryPinned()).isEqualTo(1);
        verify(subsetStatement).setString(1, OWN_AND_FOREIGN);
    }

    @Test
    void readGet_WhenPrimaryExecutedLaggingGtid_EveryReadPinnedAndCookieKept() throws Exception {
        GtidCookieFilter filter = filterOver(database(
                returning(WAIT_TIMED_OUT), returning(SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(responses).noneMatch(GtidPinExitTest::clearsCookie);
        assertThat(primaryPinned()).isEqualTo(READS);
        assertThat(subsetQueries()).isEqualTo(READS);
    }

    @Test
    void readGet_WhenReplicaCaughtUp_ClearsWithoutAskingPrimary() throws Exception {
        GtidCookieFilter filter = filterOver(database(
                returning(WAIT_APPLIED), returning(SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(clearsCookie(responses.get(0))).isTrue();
        assertThat(primaryPinned()).isZero();
        assertThat(subsetQueries()).isZero();
    }

    @Test
    void readGet_WhenWaitFailsTransiently_EveryReadPinnedAndCookieKept() throws Exception {
        GtidCookieFilter filter = filterOver(database(
                throwing(connectionFailure()), returning(NOT_SUBSET)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(responses).noneMatch(GtidPinExitTest::clearsCookie);
        assertThat(primaryPinned()).isEqualTo(READS);
        assertThat(subsetQueries()).isZero();
    }

    @Test
    void readGet_WhenSubsetCheckFails_EveryReadPinnedAndCookieKept() throws Exception {
        GtidCookieFilter filter = filterOver(database(
                returning(WAIT_TIMED_OUT), throwing(connectionFailure())));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(responses).noneMatch(GtidPinExitTest::clearsCookie);
        assertThat(primaryPinned()).isEqualTo(READS);
    }

    @Test
    void decode_WhenNotGtidSyntax_ReturnsEmpty() {
        assertThat(GtidCookie.decode(rawCookie(MALFORMED))).isEmpty();
    }

    @Test
    void registration_WhenReplicaDisabledAndPrimaryWouldReportCaughtUp_KeepsCookie() throws Exception {
        GtidReadGate primaryAnsweringGate = mock(GtidReadGate.class);
        when(primaryAnsweringGate.probe(anyString())).thenReturn(GtidReadGate.Verdict.CAUGHT_UP);
        ReplicaDataSourceProperties oregon = new ReplicaDataSourceProperties();
        GtidCookieFilter filter = new GtidGateConfig().gtidCookieFilterRegistration(
                primaryAnsweringGate, new GtidWriteCapture(meters), oregon).getFilter();

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(responses).noneMatch(GtidPinExitTest::clearsCookie);
    }

    @Test
    void registration_WhenReplicaEnabledAndGateReportsCaughtUp_ClearsCookie() throws Exception {
        GtidReadGate caughtUpGate = mock(GtidReadGate.class);
        when(caughtUpGate.probe(anyString())).thenReturn(GtidReadGate.Verdict.CAUGHT_UP);
        ReplicaDataSourceProperties seoul = new ReplicaDataSourceProperties();
        seoul.setEnabled(true);
        GtidCookieFilter filter = new GtidGateConfig().gtidCookieFilterRegistration(
                caughtUpGate, new GtidWriteCapture(meters), seoul).getFilter();

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(clearsCookie(responses.get(0))).isTrue();
    }
}
