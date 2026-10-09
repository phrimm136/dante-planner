package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.integration.consistency.Plan.FaultWindow;

import static org.assertj.core.api.Assertions.assertThat;

class FaultSchedulerTest {

    @Test
    void scheduler_WhenTheWindowEndsAfterTheLastOperation_StopsAtItsIndexAndStartsAtTheEnd() {
        List<String> events = new ArrayList<>();
        FaultScheduler scheduler = new FaultScheduler(recording(events), new FaultWindow(1, 3));

        for (int index = 0; index <= 3; index++) {
            events.add("reach " + index);
            scheduler.reach(index);
        }

        assertThat(events).containsExactly("reach 0", "reach 1", "stop", "reach 2", "reach 3", "start");
    }

    private static Replication recording(List<String> events) {
        return new Replication() {
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
        };
    }
}
