package org.danteplanner.backend.shared.freshness;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.gtid.GtidWriteCapture;


import static org.assertj.core.api.Assertions.assertThat;

class FreshnessStampAdviceTest {

    private static final String GTID = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-7";

    private static final class SingleTakeCapture extends GtidWriteCapture {

        private Optional<Capture> pending = Optional.of(new Capture(GTID, true));

        private SingleTakeCapture() {
            super(new SimpleMeterRegistry());
        }

        @Override
        public Optional<String> pollCapturedGtid() {
            return takeOnce().map(Capture::gtidSet);
        }

        @Override
        public Optional<Capture> takeCapture() {
            return takeOnce();
        }

        private Optional<Capture> takeOnce() {
            Optional<Capture> capture = pending;
            pending = Optional.empty();
            return capture;
        }
    }

    @Test
    void beforeBodyWrite_WhenTheCaptureYieldsItsGtidOnlyOnce_RaisesTheDeclaredScopesFromItAndSetsNoCookie() {
        RecordingMarkerStore markerStore = new RecordingMarkerStore();
        FreshnessStampAdvice advice = new FreshnessStampAdvice(
                new MarkerStamp(new SingleTakeCapture(), markerStore, new SimpleMeterRegistry(), false));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        FreshnessScope.Planner planner = new FreshnessScope.Planner(UUID.randomUUID());
        FreshnessDeclarations.begin();
        try {
            FreshnessDeclarations.declare(planner);

            advice.beforeBodyWrite(null, null, null, null, null, response);
        } finally {
            FreshnessDeclarations.clear();
        }

        assertThat(markerStore.raises).containsExactly(new RecordingMarkerStore.Raise(Set.of(planner), GTID));
        assertThat(response.getHeaders().headerNames()).doesNotContain(HttpHeaders.SET_COOKIE);
    }
}
