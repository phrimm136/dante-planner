package org.danteplanner.backend.integration.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.integration.consistency.Plan.Latency;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;
import org.danteplanner.backend.integration.consistency.Plan.Stopped;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseRunnerTest {

    private static final PlannedOperation READ = new PlannedOperation.Read(new KeyDraw(0));
    private static final List<List<PlannedOperation>> TWO_READS_EACH =
            List.of(List.of(READ, READ), List.of(READ, READ), List.of(READ, READ), List.of(READ, READ));

    @Test
    void plan_WhenPhaseAEnds_RestoresReplicationBeforePhaseBStopsIt() throws Exception {
        RecordingReplication replication = new RecordingReplication();
        Plan plan = new Plan(new Phase(List.of(new Round(TWO_READS_EACH)), new Latency(30)), new Phase(List.of(new Round(TWO_READS_EACH)), new Stopped()));

        PhaseRunner.runPlan(plan, sessions(replication), replication);

        assertThat(replication.events).filteredOn(event -> !event.equals("operation"))
                .containsExactly("latency 30", "remove latency", "start", "await", "stop");
        List<String> events = replication.events;
        assertThat(events.subList(0, events.indexOf("remove latency"))).filteredOn("operation"::equals).hasSize(8);
        assertThat(events.subList(events.indexOf("stop"), events.size())).filteredOn("operation"::equals)
                .hasSize(8);
    }

    @Test
    void phase_WhenTheLatencyDrawIsZero_AddsNoToxic() throws Exception {
        RecordingReplication replication = new RecordingReplication();

        PhaseRunner.run(new Phase(List.of(new Round(TWO_READS_EACH)), new Latency(0)), sessions(replication), replication);

        assertThat(replication.events).containsOnly("operation").hasSize(8);
    }

    @Test
    void phase_WhenItHasTwoRounds_CompletesTheFirstBeforeAnyOperationOfTheSecondStarts() throws Exception {
        RecordingReplication replication = new RecordingReplication();
        PlannedOperation delete = new PlannedOperation.Delete(new KeyDraw(0));
        Round deleteOnly = new Round(List.of(List.of(delete), List.of(), List.of(), List.of()));
        OperationPerformer session = operation -> replication.events.add(operation == delete ? "delete" : "read");

        PhaseRunner.run(new Phase(List.of(deleteOnly, new Round(TWO_READS_EACH)), new Stopped()),
                List.of(session, session, session, session), replication);

        assertThat(replication.events).containsExactly("stop", "delete", "read", "read", "read", "read", "read",
                "read", "read", "read");
    }

    private static List<OperationPerformer> sessions(RecordingReplication replication) {
        OperationPerformer session = operation -> replication.events.add("operation");
        return List.of(session, session, session, session);
    }
}
