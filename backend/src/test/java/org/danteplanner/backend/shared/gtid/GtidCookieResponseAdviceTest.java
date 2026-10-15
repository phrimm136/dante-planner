package org.danteplanner.backend.shared.gtid;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;

import static org.assertj.core.api.Assertions.assertThat;

class GtidCookieResponseAdviceTest {

    private static final String GTID = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-7";

    private static final class SingleTakeCapture extends GtidWriteCapture {

        private Optional<String> pending = Optional.of(GTID);

        private SingleTakeCapture() {
            super(new SimpleMeterRegistry());
        }

        @Override
        public Optional<String> pollCapturedGtid() {
            return takeOnce();
        }

        @Override
        public Optional<String> takeCapturedGtid() {
            return takeOnce();
        }

        private Optional<String> takeOnce() {
            Optional<String> gtid = pending;
            pending = Optional.empty();
            return gtid;
        }
    }

    @Test
    void beforeBodyWrite_WhenTheCaptureYieldsItsGtidOnlyOnce_RaisesTheDeclaredScopesAndMintsTheCookieFromIt() {
        RecordingMarkerStore markerStore = new RecordingMarkerStore();
        GtidCookieResponseAdvice advice = new GtidCookieResponseAdvice(new SingleTakeCapture(), markerStore);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        FreshnessScope.Entity planner = new FreshnessScope.Entity(UUID.randomUUID());
        FreshnessDeclarations.begin();
        try {
            FreshnessDeclarations.declare(planner);

            advice.beforeBodyWrite(null, null, null, null, null, response);
        } finally {
            FreshnessDeclarations.clear();
        }

        assertThat(markerStore.raises).containsExactly(new RecordingMarkerStore.Raise(Set.of(planner), GTID));
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly(GtidCookie.of(GTID).toString());
    }
}
