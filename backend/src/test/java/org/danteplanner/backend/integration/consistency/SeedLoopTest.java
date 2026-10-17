package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedLoopTest {

    @Test
    void seedLoop_WhenASeedThrowsUnderAToxicAStopAndACut_RestoresOregonThenReplication() {
        RecordingReplication replication = new RecordingReplication();

        assertThatThrownBy(() -> SeedLoop.run(List.of(1L, 2L, 3L), replication, replication, seed -> {
            replication.events.add("seed " + seed + " began");
            replication.applyLatency(seed);
            replication.stopReplica();
            replication.cut();
            if (seed == 2L) {
                throw new IllegalStateException("seed 2 failed mid-run");
            }
            return seed;
        }, seed -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("seed 2 failed mid-run");

        assertThat(replication.events).containsExactly(
                "seed 1 began", "latency 1", "stop", "cut oregon", "restore oregon", "remove latency", "start", "await",
                "seed 2 began", "latency 2", "stop", "cut oregon", "restore oregon", "remove latency", "start",
                "await");
    }

    @Test
    void seedLoop_WhenStopAfterMatches_RunsNoFurtherSeed() throws Exception {
        RecordingReplication replication = new RecordingReplication();

        List<Long> results = SeedLoop.run(List.of(1L, 2L, 3L), replication, replication, seed -> seed,
                seed -> seed == 2L);

        assertThat(results).containsExactly(1L, 2L);
    }

    @Test
    void runUntil_WhenCoverageCompletesAfterTwoSeeds_RunsExactlyTwoExtraSeeds() throws Exception {
        RecordingReplication replication = new RecordingReplication();
        AtomicInteger needMoreCalls = new AtomicInteger();

        List<Long> results = SeedLoop.runUntil(4L, 10, () -> needMoreCalls.incrementAndGet() <= 2, replication,
                replication, seed -> {
                    replication.events.add("seed " + seed + " began");
                    return seed;
                }, seed -> false);

        assertThat(results).containsExactly(4L, 5L);
        assertThat(replication.events).containsExactly(
                "seed 4 began", "restore oregon", "remove latency", "start", "await",
                "seed 5 began", "restore oregon", "remove latency", "start", "await");
    }

    @Test
    void runUntil_WhenCoverageNeverCompletes_StopsAtTheCap() throws Exception {
        RecordingReplication replication = new RecordingReplication();
        List<Long> bodyCalls = new ArrayList<>();

        List<Long> results = SeedLoop.runUntil(4L, 3, () -> true, replication, replication, seed -> {
            bodyCalls.add(seed);
            return seed;
        }, seed -> false);

        assertThat(results).containsExactly(4L, 5L, 6L);
        assertThat(bodyCalls).containsExactly(4L, 5L, 6L);
    }

    @Test
    void runUntil_WhenStopAfterMatches_RunsNoFurtherSeed() throws Exception {
        RecordingReplication replication = new RecordingReplication();

        List<Long> results = SeedLoop.runUntil(4L, 10, () -> true, replication, replication, seed -> seed,
                seed -> seed == 5L);

        assertThat(results).containsExactly(4L, 5L);
    }

    @Test
    void runUntil_WhenAnExtraSeedThrows_RestoresOregonThenReplication() {
        RecordingReplication replication = new RecordingReplication();

        assertThatThrownBy(() -> SeedLoop.runUntil(4L, 10, () -> true, replication, replication, seed -> {
            replication.events.add("seed " + seed + " began");
            replication.applyLatency(seed);
            replication.stopReplica();
            replication.cut();
            throw new IllegalStateException("seed " + seed + " failed mid-run");
        }, seed -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("seed 4 failed mid-run");

        assertThat(replication.events).containsExactly(
                "seed 4 began", "latency 4", "stop", "cut oregon", "restore oregon", "remove latency", "start",
                "await");
    }

    @Test
    void runUntil_WhenTheCapIsZero_RunsNoSeed() throws Exception {
        RecordingReplication replication = new RecordingReplication();
        List<Long> bodyCalls = new ArrayList<>();

        List<Long> results = SeedLoop.runUntil(4L, 0, () -> true, replication, replication, seed -> {
            bodyCalls.add(seed);
            return seed;
        }, seed -> false);

        assertThat(results).isEmpty();
        assertThat(bodyCalls).isEmpty();
        assertThat(replication.events).isEmpty();
    }
}
