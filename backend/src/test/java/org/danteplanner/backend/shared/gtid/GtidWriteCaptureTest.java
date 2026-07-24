package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GtidWriteCaptureTest {

    private static final String UUID_A = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String GLOBAL_GTID = UUID_A + ":1-200";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final GtidWriteCapture capture = new GtidWriteCapture(jdbcTemplate);

    @AfterEach
    void tearDown() {
        capture.clear();
    }

    @Test
    void ownGtidUnionAcrossCommits_WhenTwoCommits_UnionsGtids() {
        capture.recordCommit(UUID_A + ":100");
        capture.recordCommit(UUID_A + ":101");

        assertThat(capture.pollCapturedGtid()).contains(UUID_A + ":100-101");
    }

    @Test
    void ownGtidFallbackOnEmptyTracker_WhenTrackerEmpty_ReadsGlobalGtidExecuted() {
        when(jdbcTemplate.queryForObject("SELECT @@gtid_executed", String.class))
                .thenReturn(GLOBAL_GTID);
        capture.recordCommit(null);

        assertThat(capture.pollCapturedGtid()).contains(GLOBAL_GTID);
    }

    @Test
    void rywNoCookieOnRedisOnlyWrite_WhenNoTxCommitted_ReturnsEmpty() {
        when(jdbcTemplate.queryForObject("SELECT @@gtid_executed", String.class))
                .thenReturn(GLOBAL_GTID);

        assertThat(capture.pollCapturedGtid()).isEmpty();
    }
}
