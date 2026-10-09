package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedLoopTest {

    @Test
    void seedLoop_WhenASeedThrowsAfterStoppingReplication_RestoresReplicationBeforeTheNextSeed() {
        RecordingReplication replication = new RecordingReplication();

        assertThatThrownBy(() -> SeedLoop.run(List.of(1L, 2L, 3L), replication, seed -> {
            replication.events.add("seed " + seed + " began");
            replication.stopReplica();
            if (seed == 2L) {
                throw new IllegalStateException("seed 2 failed mid-run");
            }
            return seed;
        }, seed -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("seed 2 failed mid-run");

        assertThat(replication.events).containsExactly(
                "seed 1 began", "stop", "start", "await",
                "seed 2 began", "stop", "start", "await");
    }

    @Test
    void seedLoop_WhenStopAfterMatches_RunsNoFurtherSeed() throws Exception {
        RecordingReplication replication = new RecordingReplication();

        List<Long> results = SeedLoop.run(List.of(1L, 2L, 3L), replication, seed -> seed, seed -> seed == 2L);

        assertThat(results).containsExactly(1L, 2L);
    }

    private static final class RecordingReplication implements Replication {

        private final List<String> events = new ArrayList<>();

        @Override
        public void stopReplica() {
            events.add("stop");
        }

        @Override
        public void startReplica() {
            events.add("start");
        }

        @Override
        public void awaitCaughtUp() {
            events.add("await");
        }
    }
}
