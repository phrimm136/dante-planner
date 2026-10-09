package org.danteplanner.backend.integration.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedLoopTest {

    @Test
    void seedLoop_WhenASeedThrowsUnderAToxicAndAStop_RemovesTheToxicAndRestoresReplication() {
        RecordingReplication replication = new RecordingReplication();

        assertThatThrownBy(() -> SeedLoop.run(List.of(1L, 2L, 3L), replication, seed -> {
            replication.events.add("seed " + seed + " began");
            replication.applyLatency(seed);
            replication.stopReplica();
            if (seed == 2L) {
                throw new IllegalStateException("seed 2 failed mid-run");
            }
            return seed;
        }, seed -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("seed 2 failed mid-run");

        assertThat(replication.events).containsExactly(
                "seed 1 began", "latency 1", "stop", "remove latency", "start", "await",
                "seed 2 began", "latency 2", "stop", "remove latency", "start", "await");
    }

    @Test
    void seedLoop_WhenStopAfterMatches_RunsNoFurtherSeed() throws Exception {
        RecordingReplication replication = new RecordingReplication();

        List<Long> results = SeedLoop.run(List.of(1L, 2L, 3L), replication, seed -> seed, seed -> seed == 2L);

        assertThat(results).containsExactly(1L, 2L);
    }
}
