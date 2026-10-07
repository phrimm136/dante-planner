package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;

class GtidCookieFilterTest {

    private static final String GTID = "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";

    private final ScriptedReadGate readGate = new ScriptedReadGate();
    private final GtidWriteCapture writeCapture = new GtidWriteCapture(new SimpleMeterRegistry());
    private final GtidCookieFilter filter = new GtidCookieFilter(readGate, writeCapture, true);
    private final GtidCookieFilter replicalessFilter =
            new GtidCookieFilter(readGate, writeCapture, false);
    private final ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(
            new UndeclaredPrimaryAccessGuard(new SimpleMeterRegistry(), true));
    private final List<Object> routedTo = new ArrayList<>();

    private static final class ScriptedReadGate extends GtidReadGate {

        private final List<String> probed = new ArrayList<>();
        private Verdict verdict;

        private ScriptedReadGate() {
            super(new SimpleDriverDataSource(), new SimpleMeterRegistry());
        }

        private void answers(Verdict scripted) {
            verdict = scripted;
        }

        @Override
        public Verdict probe(String gtid) {
            probed.add(gtid);
            return verdict;
        }
    }

    private FilterChain committing(String gtid) {
        return (request, response) -> {
            routedTo.add(readOnlyRoute());
            writeCapture.recordCommit(gtid, true);
        };
    }

    private FilterChain reading() {
        return (request, response) -> routedTo.add(readOnlyRoute());
    }

    private Object readOnlyRoute() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            return routing.determineCurrentLookupKey();
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }

    @Test
    void readGet_WhenNoCookie_PassesThroughWithoutGateOrPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(readGate.probed).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void readGet_WhenCookieCaughtUp_ClearsCookieAndDoesNotPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        readGate.answers(GtidReadGate.Verdict.CAUGHT_UP);

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(readGate.probed).containsExactly(GTID);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=")
                        .contains("Max-Age=0"));
    }

    @Test
    void readGet_WhenCookieNotCaughtUp_PinsPrimaryRetainsCookieAndClearsPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        readGate.answers(GtidReadGate.Verdict.NOT_CAUGHT_UP);

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.PRIMARY);
        assertThat(readOnlyRoute()).isEqualTo(RoutingKey.REPLICA);

        assertThat(readGate.probed).containsExactly(GTID);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void readGet_WhenNotCaughtUpAndChainThrows_StillClearsPin() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        readGate.answers(GtidReadGate.Verdict.NOT_CAUGHT_UP);
        RuntimeException boom = new RuntimeException("chain blew up");
        FilterChain throwingChain = (req, res) -> {
            routedTo.add(readOnlyRoute());
            throw boom;
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                .isSameAs(boom);

        assertThat(routedTo).containsExactly(RoutingKey.PRIMARY);
        assertThat(readOnlyRoute()).isEqualTo(RoutingKey.REPLICA);
    }

    @Test
    void write_WhenCaptureHasGtid_SetsGtidCookieAndSkipsReadGate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, committing(GTID));

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(GTID).getValue())
                        .contains("HttpOnly")
                        .contains("Secure")
                        .contains("SameSite=Lax"));
        assertThat(readGate.probed).isEmpty();
    }

    @Test
    void write_WhenCaptureHasMultiUuidGtidSet_SetsCookieWithoutError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String gtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-100,"
                + "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50";

        filter.doFilter(request, response, committing(gtidSet));

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(gtidSet).getValue()));
    }

    @Test
    void oauthGoogleCallbackMintsCookie_WhenGetCommitsTx_SetsCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, committing(GTID));

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(GTID).getValue()));
    }

    @Test
    void readGet_WhenCookieAndNoReplica_ServesWithoutGateOrPinAndKeepsCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        replicalessFilter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(readGate.probed).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void write_WhenNoReplicaAndCaptureHasGtid_SetsGtidCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/planners");
        MockHttpServletResponse response = new MockHttpServletResponse();

        replicalessFilter.doFilter(request, response, committing(GTID));

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(GTID).getValue()));
    }

    @Test
    void readGet_WhenCookieValueUndecodable_TreatedAsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, "not*base64url*value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(readGate.probed).isEmpty();
    }

    @Test
    void readGet_WhenGtidNeverExecutedOnPrimary_PinsPrimaryAndClearsCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        readGate.answers(GtidReadGate.Verdict.NEVER_EXECUTED);

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.PRIMARY);
        assertThat(readOnlyRoute()).isEqualTo(RoutingKey.REPLICA);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=")
                        .contains("Max-Age=0"));
    }

    @Test
    void readGet_WhenGateReportsUnusable_ClearsCookieAndDoesNotPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of(GTID).getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        readGate.answers(GtidReadGate.Verdict.UNUSABLE);

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=")
                        .contains("Max-Age=0"));
    }

    @Test
    void readGet_WhenCookieIsNotGtidSyntax_ClearsCookieWithoutGateOrPin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of("not-a-gtid").getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, reading());

        assertThat(routedTo).containsExactly(RoutingKey.REPLICA);

        assertThat(readGate.probed).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .startsWith(GtidCookie.NAME + "=;")
                        .contains("Path=/")
                        .contains("Max-Age=0")
                        .contains("Secure")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"));
    }

    @Test
    void readGet_WhenCookieIsNotGtidSyntaxAndNoReplica_KeepsCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, GtidCookie.of("not-a-gtid").getValue()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        replicalessFilter.doFilter(request, response, new MockFilterChain());

        assertThat(readGate.probed).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void readGet_WhenCookieValueUndecodable_KeepsCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/planners");
        request.setCookies(new Cookie(GtidCookie.NAME, "not*base64url*value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}
