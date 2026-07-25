package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GtidWriteCaptureTest {

    private static final String UUID_A = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String GLOBAL_GTID = UUID_A + ":1-200";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final GtidWriteCapture capture = new GtidWriteCapture(jdbcTemplate);

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
    void ownGtidFallbackOnEmptyTracker_WhenTrackerEmpty_ReadsGlobalGtidExecuted() {
        when(jdbcTemplate.queryForObject("SELECT @@gtid_executed", String.class))
                .thenReturn(GLOBAL_GTID);
        capture.recordCommit(null, false);

        assertThat(capture.pollCapturedGtid()).contains(GLOBAL_GTID);
    }

    @Test
    void ownGtidFallbackOnEmptyTracker_WhenOneCommitOfTwoMissesGtid_ReadsGlobalGtidExecuted() {
        when(jdbcTemplate.queryForObject("SELECT @@gtid_executed", String.class))
                .thenReturn(GLOBAL_GTID);
        capture.recordCommit(UUID_A + ":100", true);
        capture.recordCommit(null, false);

        assertThat(capture.pollCapturedGtid())
                .as("a union covering only part of the request would gate less than it claims")
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
    void publishIdempotentStateTargeted_WhenCommitWroteNothing_MintsNoCookie() {
        // A real GTID first, so the tracker has proven it reports; only then does silence mean
        // "wrote nothing" rather than "cannot tell".
        capture.recordCommit(UUID_A + ":100", true);
        capture.clear();
        capture.begin();

        capture.recordCommit(null, true);

        assertThat(capture.pollCapturedGtid())
                .as("an idempotent no-op leaves no trace, so it must gate no read")
                .isEmpty();
    }

    @Test
    void ownGtidFallbackOnEmptyTracker_WhenServerNotTracking_ReadsGlobalGtidExecuted() {
        when(jdbcTemplate.queryForObject("SELECT @@gtid_executed", String.class))
                .thenReturn(GLOBAL_GTID);

        capture.recordCommit(null, true);

        assertThat(capture.pollCapturedGtid())
                .as("with tracking off, no reported GTID cannot be read as no write")
                .contains(GLOBAL_GTID);
    }

    @Test
    void rywNoCookieOnRedisOnlyWrite_WhenNoTxCommitted_ReturnsEmpty() {
        when(jdbcTemplate.queryForObject("SELECT @@gtid_executed", String.class))
                .thenReturn(GLOBAL_GTID);

        assertThat(capture.pollCapturedGtid()).isEmpty();
    }
}
