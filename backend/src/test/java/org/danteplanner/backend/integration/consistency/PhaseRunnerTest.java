package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.integration.consistency.Plan.FaultWindow;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseRunnerTest {

    private static final long OTHER_SESSIONS_HEAD_START_MILLIS = 200;

    @Test
    void phase_WhenTheFaultStopsAtIndexZero_StopsBeforeAnySessionOperates() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch otherSessionOperated = new CountDownLatch(1);
        PlannedOperation read = new PlannedOperation.Read(Slot.K1);
        Phase phase = new Phase(
                List.of(new Round(List.of(List.of(new PlannedOperation.Delete(Slot.K1)), List.of(read), List.of(read))),
                        new Round(List.of(List.of(read), List.of(read), List.of(read)))),
                new FaultWindow(0, 2));
        List<OperationPerformer> sessions = List.of(
                operation -> events.add("s1 " + operation),
                operation -> {
                    events.add("s2 " + operation);
                    otherSessionOperated.countDown();
                },
                operation -> events.add("s3 " + operation));

        PhaseRunner.run(phase, sessions, recording(events, otherSessionOperated));

        assertThat(events).hasSize(8).startsWith("stop").endsWith("start").containsOnlyOnce("stop", "start");
    }

    private static Replication recording(List<String> events, CountDownLatch otherSessionOperated) {
        return new Replication() {
            @Override
            public void stopReplica() {
                try {
                    otherSessionOperated.await(OTHER_SESSIONS_HEAD_START_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
        };
    }
}
