package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The capture is a per-request accumulator and nothing else: resolving a commit's GTID — tracker
 * first, primary's executed set as the fallback — belongs to {@link GtidCapturingDataSource}, which
 * holds the connection that committed.
 */
class GtidWriteCaptureTest {

    private static final String UUID_A = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String GLOBAL_GTID = UUID_A + ":1-200";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final GtidWriteCapture capture = new GtidWriteCapture(meterRegistry);

    @BeforeEach
    void openWindow() {
        capture.begin();
    }

    @AfterEach
    void tearDown() {
        capture.clear();
    }

    @Test
    void ownGtidUnionAcrossCommits_WhenTwoCommits_UnionsGtids() {
        capture.recordCommit(UUID_A + ":100", true);
        capture.recordCommit(UUID_A + ":101", true);

        assertThat(capture.pollCapturedGtid()).contains(UUID_A + ":100-101");
    }

    @Test
    void ownGtidUnionAcrossCommits_WhenOneCommitSuppliedTheSuperset_CoversBoth() {
        capture.recordCommit(UUID_A + ":100", true);
        capture.recordCommit(GLOBAL_GTID, false);

        assertThat(capture.pollCapturedGtid())
                .as("a union covering only part of the request would raise the marker below a commit")
                .contains(GLOBAL_GTID);
    }

    @Test
    void ownGtidUnionAcrossCommits_WhenNoWindowOpen_RecordsNothing() {
        capture.clear();
        capture.recordCommit(UUID_A + ":100", true);

        assertThat(capture.pollCapturedGtid())
                .as("a commit on a thread serving no request must leave no state behind")
                .isEmpty();
    }

    @Test
    void publishIdempotentStateTargeted_WhenCommitWroteNothing_CapturesNothing() {
        capture.recordCommit(null, true);

        assertThat(capture.pollCapturedGtid())
                .as("an idempotent no-op leaves no trace, so it must raise no marker")
                .isEmpty();
    }

    @Test
    void redisOnlyWrite_WhenNoTxCommitted_ReturnsEmpty() {
        assertThat(capture.pollCapturedGtid()).isEmpty();
    }

    @Test
    void gtidCaptureCounter_WhenTrackerAndFallbackBothRecorded_TagsEachSource() {
        capture.recordCommit(UUID_A + ":100", true);
        capture.recordCommit(GLOBAL_GTID, false);

        assertThat(meterRegistry.counter("gtid.capture", "source", "tracker").count())
                .as("a silent degradation is only visible if the branch is counted")
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("gtid.capture", "source", "fallback").count())
                .isEqualTo(1.0);
    }

    @Test
    void takeCapture_WhenOnlyTheFallbackRecorded_IsUntracked() {
        capture.recordCommit(GLOBAL_GTID, false);

        assertThat(capture.takeCapture()).contains(new GtidWriteCapture.Capture(GLOBAL_GTID, false));
    }

    @Test
    void takeCapture_WhenAFallbackAndATrackerCommitRecorded_IsTracked() {
        capture.recordCommit(GLOBAL_GTID, false);
        capture.recordCommit(UUID_A + ":201", true);

        assertThat(capture.takeCapture()).contains(new GtidWriteCapture.Capture(UUID_A + ":1-201", true));
    }

    @Test
    void takeCapture_WhenTaken_LeavesNothingAndForgetsTheTrackedFlag() {
        capture.recordCommit(UUID_A + ":100", true);
        capture.takeCapture();

        assertThat(capture.takeCapture()).isEmpty();
        capture.recordCommit(GLOBAL_GTID, false);
        assertThat(capture.takeCapture()).contains(new GtidWriteCapture.Capture(GLOBAL_GTID, false));
    }
}
