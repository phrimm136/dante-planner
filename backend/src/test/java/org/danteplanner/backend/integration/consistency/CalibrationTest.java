package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.BISECTION_DEPTH_BUDGET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DOUBLING_BUDGET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.PROBE_BATCH_BUDGET;

class CalibrationTest {

    private static final long BOUNDARY_MILLIS = 37;

    @Test
    void calibration_WhenProbesTimeOutFromThirtySevenMillis_FindsThirtySevenWithinTheDepthBudget()
            throws Exception {
        List<Long> probed = new ArrayList<>();

        Calibration.Result result = Calibration.find(latency -> {
            probed.add(latency);
            return latency >= BOUNDARY_MILLIS;
        }, PROBE_BATCH_BUDGET, DOUBLING_BUDGET, BISECTION_DEPTH_BUDGET);

        assertThat(result).isEqualTo(new Calibration.Result.Boundary(BOUNDARY_MILLIS, PROBE_BATCH_BUDGET, 5));
        assertThat(probed).filteredOn(latency -> latency == BOUNDARY_MILLIS).hasSize(PROBE_BATCH_BUDGET);
        assertThat(result).hasToString("L*=37 (batch 5, depth 5)");
    }

    @Test
    void calibration_WhenTheFirstLatencyAlreadyTimesOut_ReturnsItWithoutBisecting() throws Exception {
        Calibration.Result result = Calibration.find(latency -> true, PROBE_BATCH_BUDGET, DOUBLING_BUDGET,
                BISECTION_DEPTH_BUDGET);

        assertThat(result).isEqualTo(new Calibration.Result.Boundary(1, PROBE_BATCH_BUDGET, 0));
    }

    @Test
    void calibration_WhenOneProbeInABatchIsServedByTheReplica_TreatsTheLatencyAsApplied() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        Calibration.Result result = Calibration.find(latency -> latency >= 64 || calls.getAndIncrement() % 2 == 0, 2,
                DOUBLING_BUDGET, BISECTION_DEPTH_BUDGET);

        assertThat(result.latencyMillis()).isEqualTo(64);
    }

    @Test
    void calibration_WhenNoLatencyTimesOut_IsUnreachedAtTheLargestLatencyTried() throws Exception {
        long largest = 1L << DOUBLING_BUDGET;

        Calibration.Result result = Calibration.find(latency -> false, PROBE_BATCH_BUDGET, DOUBLING_BUDGET,
                BISECTION_DEPTH_BUDGET);

        assertThat(result).isEqualTo(new Calibration.Result.Unreached(largest, PROBE_BATCH_BUDGET))
                .hasToString("no replication latency up to " + largest
                        + " ms timed out the marker wait on a whole batch of " + PROBE_BATCH_BUDGET + " probes");
    }
}
