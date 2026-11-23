package org.danteplanner.backend.shared.freshness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreshnessStampFilterTest {

    private static final String GTID = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-5";
    private static final String OTHER_GTID = "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50";

    private final GtidWriteCapture writeCapture = new GtidWriteCapture(new SimpleMeterRegistry());
    private final RecordingMarkerStore markerStore = new RecordingMarkerStore();
    private final FreshnessStampFilter filter = new FreshnessStampFilter(writeCapture,
            new MarkerStamp(writeCapture, markerStore, new SimpleMeterRegistry(), false));

    private static final class CookieRefusingRequest extends MockHttpServletRequest {

        private CookieRefusingRequest(String method) {
            super(method, "/api/planners");
        }

        @Override
        public Cookie[] getCookies() {
            throw new AssertionError("the stamp filter read a cookie");
        }
    }

    private FilterChain declaring(String gtid, FreshnessScope... scopes) {
        return (request, response) -> {
            FreshnessDeclarations.declare(scopes);
            if (gtid != null) {
                writeCapture.recordCommit(gtid, true);
            }
        };
    }

    @Test
    void readGet_WhenTheRequestCarriesCookies_ReadsNoneAndSetsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<Object> served = new ArrayList<>();

        filter.doFilter(new CookieRefusingRequest("GET"), response, (req, res) -> served.add(req));

        assertThat(served).hasSize(1);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void write_WhenItCommits_SetsNoCookie() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new CookieRefusingRequest("POST"), response, declaring(GTID, new FreshnessScope.Global()));

        assertThat(markerStore.raises).hasSize(1);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void write_WhenItCommitsOnSeveralServers_RaisesToTheWholeSet() throws Exception {
        FreshnessScope catalog = new FreshnessScope.Global();

        filter.doFilter(new MockHttpServletRequest("PUT", "/api/planners"), new MockHttpServletResponse(),
                declaring(GTID + "," + OTHER_GTID, catalog));

        assertThat(markerStore.raises).containsExactly(
                new RecordingMarkerStore.Raise(Set.of(catalog), GTID + "," + OTHER_GTID));
    }

    @Test
    void oauthCallbackGet_WhenItCommits_RaisesTheDeclaredScopes() throws Exception {
        FreshnessScope user = new FreshnessScope.User(7);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/auth/google/callback"),
                new MockHttpServletResponse(), declaring(GTID, user));

        assertThat(markerStore.raises).containsExactly(new RecordingMarkerStore.Raise(Set.of(user), GTID));
    }

    @Test
    void request_WhenTheChainThrows_ClearsTheChosenReadSource() {
        RuntimeException boom = new RuntimeException("chain blew up");
        FilterChain throwingChain = (req, res) -> {
            ChosenReadSource.record("seoul-replica", false);
            throw boom;
        };

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("GET", "/api/planners"), new MockHttpServletResponse(), throwingChain))
                .isSameAs(boom);

        assertThat(ChosenReadSource.name()).isEmpty();
    }

    @Test
    void request_WhenAStaleSourceIsRecordedOnTheThread_StartsWithNoneRecorded() throws Exception {
        ChosenReadSource.record("seoul-replica", false);
        List<Optional<String>> recorded = new ArrayList<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/planners"), new MockHttpServletResponse(),
                (req, res) -> recorded.add(ChosenReadSource.name()));

        assertThat(recorded).containsExactly(Optional.empty());
    }

    @Test
    void bodilessWrite_WhenItCommitsAndDeclares_RaisesTheDeclaredScopesToItsGtid() throws Exception {
        FreshnessScope entity = new FreshnessScope.Planner(UUID.randomUUID());
        FreshnessScope catalog = new FreshnessScope.Global();

        filter.doFilter(new MockHttpServletRequest("DELETE", "/api/planners"), new MockHttpServletResponse(),
                declaring(GTID, entity, catalog));

        assertThat(markerStore.raises).containsExactly(
                new RecordingMarkerStore.Raise(Set.of(entity, catalog), GTID));
    }

    @Test
    void write_WhenItDeclaresButCommitsNothing_RaisesNothing() throws Exception {
        filter.doFilter(new MockHttpServletRequest("PUT", "/api/planners"), new MockHttpServletResponse(),
                declaring(null, new FreshnessScope.Global()));

        assertThat(markerStore.raises).isEmpty();
    }

    @Test
    void readGet_WhenItCommitsNothing_RaisesNothing() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/planners"), new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(markerStore.raises).isEmpty();
    }

    @Test
    void request_WhenItEnds_LeavesNoDeclarationOnTheThread() throws Exception {
        filter.doFilter(new MockHttpServletRequest("POST", "/api/planners"), new MockHttpServletResponse(),
                declaring(null, new FreshnessScope.Global()));

        FreshnessDeclarations.declare(new FreshnessScope.User(1L));
        assertThat(FreshnessDeclarations.declared()).isEmpty();
    }

    @Test
    void request_WhenAStaleDeclarationIsOnTheThread_StartsWithNoneDeclared() throws Exception {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declare(new FreshnessScope.Global());
        FreshnessScope entity = new FreshnessScope.Planner(UUID.randomUUID());

        filter.doFilter(new MockHttpServletRequest("POST", "/api/planners"), new MockHttpServletResponse(),
                declaring(GTID, entity));

        assertThat(markerStore.raises).containsExactly(new RecordingMarkerStore.Raise(Set.of(entity), GTID));
    }

    @Test
    void request_WhenTheChainThrows_ClearsTheDeclarationsAndTheCapture() {
        RuntimeException boom = new RuntimeException("chain blew up");
        FilterChain throwingChain = (req, res) -> {
            FreshnessDeclarations.declare(new FreshnessScope.Global());
            writeCapture.recordCommit(GTID, true);
            throw boom;
        };

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("POST", "/api/planners"), new MockHttpServletResponse(), throwingChain))
                .isSameAs(boom);

        FreshnessDeclarations.declare(new FreshnessScope.User(1L));
        assertThat(FreshnessDeclarations.declared()).isEmpty();
        assertThat(writeCapture.isWindowOpen()).isFalse();
        assertThat(markerStore.raises).isEmpty();
    }
}
