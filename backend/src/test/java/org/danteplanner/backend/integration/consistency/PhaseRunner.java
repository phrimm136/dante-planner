package org.danteplanner.backend.integration.consistency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.danteplanner.backend.integration.consistency.Plan.CutOregon;
import org.danteplanner.backend.integration.consistency.Plan.Fault;
import org.danteplanner.backend.integration.consistency.Plan.Latency;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;
import org.danteplanner.backend.integration.consistency.Plan.Stopped;

public final class PhaseRunner {

    private PhaseRunner() {
    }

    public static Duration runPlan(Plan plan, List<? extends OperationPerformer> sessions, Replication replication,
            OregonPrimary oregon) throws InterruptedException, ExecutionException {
        run(plan.phaseA(), sessions, replication, oregon);
        replication.removeLatency();
        replication.startReplica();
        replication.awaitCaughtUp();
        run(plan.phaseB(), sessions, replication, oregon);
        Duration phaseC = run(plan.phaseC(), sessions, replication, oregon);
        oregon.restore();
        return phaseC;
    }

    static Duration run(Phase phase, List<? extends OperationPerformer> sessions, Replication replication,
            OregonPrimary oregon) throws InterruptedException, ExecutionException {
        apply(phase.fault(), replication, oregon);
        long started = System.nanoTime();
        for (Round round : phase.rounds()) {
            run(round, sessions);
        }
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static void run(Round round, List<? extends OperationPerformer> sessions)
            throws InterruptedException, ExecutionException {
        try (ExecutorService executor = Executors.newFixedThreadPool(sessions.size())) {
            List<Future<Void>> running = new ArrayList<>();
            for (int index = 0; index < sessions.size(); index++) {
                OperationPerformer session = sessions.get(index);
                List<PlannedOperation> operations = round.bySession().get(index);
                running.add(executor.submit(() -> {
                    for (PlannedOperation operation : operations) {
                        session.perform(operation);
                    }
                    return null;
                }));
            }
            for (Future<Void> session : running) {
                session.get();
            }
        }
    }

    private static void apply(Fault fault, Replication replication, OregonPrimary oregon) {
        switch (fault) {
            case Latency latency -> {
                if (latency.millis() > 0) {
                    replication.applyLatency(latency.millis());
                }
            }
            case Stopped stopped -> replication.stopReplica();
            case CutOregon cut -> oregon.cut();
        }
    }
}
