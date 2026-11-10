package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkerStampTest {

    private static final String SOURCE = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String TRACKED_GTID = SOURCE + ":7";
    private static final String FALLBACK_GTID = SOURCE + ":1-6";

    private record Raise(Set<FreshnessScope> scopes, String gtidSet) {
    }

    private static final class RecordingMarkerStore extends MarkerStore {

        private final List<Raise> raises = new ArrayList<>();

        RecordingMarkerStore() {
            super(null, null, List.of(), new Settings(0, Duration.ofSeconds(1), Duration.ofHours(1), false),
                    new SimpleMeterRegistry());
        }

        @Override
        public void raise(Collection<FreshnessScope> scopes, String gtidSet) {
            raises.add(new Raise(Set.copyOf(scopes), gtidSet));
        }
    }

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final GtidWriteCapture writeCapture = new GtidWriteCapture(meters);
    private final RecordingMarkerStore markerStore = new RecordingMarkerStore();

    @BeforeEach
    void openWindow() {
        writeCapture.begin();
        FreshnessDeclarations.begin();
    }

    @AfterEach
    void closeWindow() {
        writeCapture.clear();
        FreshnessDeclarations.clear();
    }

    private MarkerStamp stamp(boolean failFast) {
        return new MarkerStamp(writeCapture, markerStore, meters, failFast);
    }

    private double undeclared() {
        return meters.counter(MarkerStamp.UNDECLARED_COUNTER).count();
    }

    @Test
    void stamp_WhenFailFastAndATrackedCommitDeclaredNothing_ThrowsAndRaisesNothing() {
        MarkerStamp stamp = stamp(true);
        writeCapture.recordCommit(TRACKED_GTID, true);

        assertThatThrownBy(stamp::stamp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a window committed a write and declared no freshness scope: call "
                        + "FreshnessDeclarations.declare(...) or declareNone(reason) in the service that wrote");
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenNotFailFastAndATrackedCommitDeclaredNothing_ReturnsTheGtidAndCountsIt() {
        MarkerStamp stamp = stamp(false);
        writeCapture.recordCommit(TRACKED_GTID, true);

        assertThat(stamp.stamp()).contains(SOURCE + ":7-7");
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isEqualTo(1.0);
    }

    @Test
    void stamp_WhenNothingWasCaptured_ReturnsEmptyAndCountsNothing() {
        MarkerStamp stamp = stamp(true);

        assertThat(stamp.stamp()).isEmpty();
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenScopesAreDeclared_RaisesThemToTheGtidOnceAndNothingOnTheSecondStamp() {
        MarkerStamp stamp = stamp(true);
        FreshnessScope user = new FreshnessScope.User(7);
        writeCapture.recordCommit(TRACKED_GTID, true);
        FreshnessDeclarations.declare(user);

        assertThat(stamp.stamp()).contains(SOURCE + ":7-7");
        assertThat(stamp.stamp()).isEmpty();
        assertThat(markerStore.raises).containsExactly(new Raise(Set.of(user), SOURCE + ":7-7"));
    }

    @Test
    void stamp_WhenNoneIsDeclaredInFailFast_ReturnsTheGtidAndRaisesNothing() {
        MarkerStamp stamp = stamp(true);
        writeCapture.recordCommit(TRACKED_GTID, true);
        FreshnessDeclarations.declareNone("no read renders it");

        assertThat(stamp.stamp()).contains(SOURCE + ":7-7");
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenOnlyTheFallbackCapturedAndNothingIsDeclaredInFailFast_ReturnsTheGtidAndCountsNothing() {
        MarkerStamp stamp = stamp(true);
        writeCapture.recordCommit(FALLBACK_GTID, false);

        assertThat(stamp.stamp()).contains(FALLBACK_GTID);
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenOnlyTheFallbackCapturedAndNothingIsDeclaredOutsideFailFast_ReturnsTheGtidAndCountsNothing() {
        MarkerStamp stamp = stamp(false);
        writeCapture.recordCommit(FALLBACK_GTID, false);

        assertThat(stamp.stamp()).contains(FALLBACK_GTID);
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenAFallbackAndATrackedCommitDeclaredNothingInFailFast_Throws() {
        MarkerStamp stamp = stamp(true);
        writeCapture.recordCommit(FALLBACK_GTID, false);
        writeCapture.recordCommit(TRACKED_GTID, true);

        assertThatThrownBy(stamp::stamp).isInstanceOf(IllegalStateException.class);
        assertThat(markerStore.raises).isEmpty();
    }
}
