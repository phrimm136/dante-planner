package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.sql.DataSource;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.support.LogCapture;

@ExtendWith(MockitoExtension.class)
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

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement waitStatement;

    @Mock
    private PreparedStatement subsetStatement;

    private final List<String> preparedSql = new ArrayList<>();

    private static String rawCookie(String gtid) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(gtid.getBytes(StandardCharsets.UTF_8));
    }

    private static Answer<ResultSet> returning(int value) {
        return invocation -> {
            CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
            RowSetMetaDataImpl metaData = new RowSetMetaDataImpl();
            metaData.setColumnCount(1);
            metaData.setColumnType(1, Types.INTEGER);
            rows.setMetaData(metaData);
            rows.moveToInsertRow();
            rows.updateInt(1, value);
            rows.insertRow();
            rows.moveToCurrentRow();
            rows.beforeFirst();
            return rows;
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

    private DataSource database(Answer<ResultSet> waitAnswer) throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            preparedSql.add(sql);
            return sql.contains(SUBSET_FUNCTION) ? subsetStatement : waitStatement;
        });
        when(waitStatement.executeQuery()).thenAnswer(waitAnswer);
        return dataSource;
    }

    private DataSource database(Answer<ResultSet> waitAnswer, Answer<ResultSet> subsetAnswer)
            throws SQLException {
        database(waitAnswer);
        when(subsetStatement.executeQuery()).thenAnswer(subsetAnswer);
        return dataSource;
    }

    private static ReadSourceSelector.Source source(String name, boolean authoritative, DataSource pool) {
        return new ReadSourceSelector.Source(name, authoritative, pool, CircuitBreaker.ofDefaults(name));
    }

    private GtidReadGate seoulGateOver(DataSource dataSource) {
        return new GtidReadGate(new ReadSourceSelector(List.of(
                source("seoul-replica", false, dataSource),
                source("primary", true, dataSource))), meters);
    }

    private GtidCookieFilter filterOver(DataSource dataSource) {
        return new GtidCookieFilter(seoulGateOver(dataSource), new GtidWriteCapture(meters),
                new RecordingMarkerStore(), new ProblemWriter(new ObjectMapper()));
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
        GtidCookieFilter filter = filterOver(dataSource);

        try (LogCapture logs = LogCapture.onThisThread(GtidReadGate.class)) {
            List<MockHttpServletResponse> responses = consecutiveReads(filter, MALFORMED);

            assertThat(clearsCookie(responses.get(0))).as("first response clears").isTrue();
            assertThat(primaryPinned()).isZero();
            assertThat(waitQueries()).isZero();
            assertThat(logs.at(Level.WARN)).hasSizeLessThanOrEqualTo(1);
        }
    }

    @Test
    void readGet_WhenReplicaRejectsGtidAsMalformed_ClearsUnpinnedAndWarnsOnceWithoutStack()
            throws Exception {
        GtidCookieFilter filter = filterOver(database(throwing(malformedGtidSet(FOREIGN_UUID))));

        try (LogCapture logs = LogCapture.onThisThread(GtidReadGate.class)) {
            List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

            assertThat(clearsCookie(responses.get(0))).as("first response clears").isTrue();
            assertThat(primaryPinned()).isZero();
            assertThat(subsetQueries()).isZero();
            assertThat(logs.at(Level.WARN)).singleElement()
                    .extracting(ILoggingEvent::getThrowableProxy).isNull();
        }
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
        GtidCookieFilter filter = filterOver(database(returning(WAIT_APPLIED)));

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(clearsCookie(responses.get(0))).isTrue();
        assertThat(primaryPinned()).isZero();
        assertThat(subsetQueries()).isZero();
    }

    @Test
    void readGet_WhenWaitFailsTransiently_EveryReadPinnedAndCookieKept() throws Exception {
        GtidCookieFilter filter = filterOver(database(throwing(connectionFailure())));

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
    void registration_WhenThePrimaryReadsFirstAndWouldReportCaughtUp_KeepsCookie() throws Exception {
        GtidReadGate primaryAnsweringGate = new GtidReadGate(new ReadSourceSelector(List.of(
                source("primary", true, dataSource),
                source("seoul-replica", false, dataSource))), meters);
        GtidCookieFilter filter = new GtidGateConfig().gtidCookieFilterRegistration(
                primaryAnsweringGate, new GtidWriteCapture(meters), new RecordingMarkerStore(),
                new ProblemWriter(new ObjectMapper())).getFilter();

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(responses).noneMatch(GtidPinExitTest::clearsCookie);
    }

    @Test
    void registration_WhenTheReplicaReadsFirstAndGateReportsCaughtUp_ClearsCookie() throws Exception {
        GtidReadGate caughtUpGate = seoulGateOver(database(returning(WAIT_APPLIED)));
        GtidCookieFilter filter = new GtidGateConfig().gtidCookieFilterRegistration(
                caughtUpGate, new GtidWriteCapture(meters), new RecordingMarkerStore(),
                new ProblemWriter(new ObjectMapper())).getFilter();

        List<MockHttpServletResponse> responses = consecutiveReads(filter, FOREIGN_UUID);

        assertThat(clearsCookie(responses.get(0))).isTrue();
    }
}
