package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;

public final class PhaseRunner {

    private static final int FAULT_SESSION = 0;

    private PhaseRunner() {
    }

    public static StaleWindow runPlan(Plan plan, List<? extends OperationPerformer> sessions,
            Replication replication, LongSupplier clock) throws InterruptedException, ExecutionException {
        StampingReplication phaseA = new StampingReplication(replication, clock);
        run(plan.phaseA(), sessions, phaseA);
        replication.awaitCaughtUp();
        run(plan.phaseB(), sessions, replication);
        return phaseA.window();
    }

    static void run(Phase phase, List<? extends OperationPerformer> sessions, Replication replication)
            throws InterruptedException, ExecutionException {
        FaultScheduler faults = new FaultScheduler(replication, phase.fault());
        faults.reach(0);
        int reached = 0;
        for (Round round : phase.rounds()) {
            run(round, sessions, faults, reached);
            reached += round.bySession().get(FAULT_SESSION).size();
        }
        faults.reach(reached);
    }

    private static void run(Round round, List<? extends OperationPerformer> sessions, FaultScheduler faults,
            int offset)
            throws InterruptedException, ExecutionException {
        try (ExecutorService executor = Executors.newFixedThreadPool(sessions.size())) {
            List<Future<Void>> running = new ArrayList<>();
            for (int index = 0; index < sessions.size(); index++) {
                OperationPerformer session = sessions.get(index);
                List<PlannedOperation> operations = round.bySession().get(index);
                boolean faultSession = index == FAULT_SESSION;
                running.add(executor.submit(() -> {
                    for (int step = 0; step < operations.size(); step++) {
                        if (faultSession && offset + step > 0) {
                            faults.reach(offset + step);
                        }
                        session.perform(operations.get(step));
                    }
                    return null;
                }));
            }
            for (Future<Void> session : running) {
                session.get();
            }
        }
    }

    private static final class StampingReplication implements Replication {

        private final Replication replication;
        private final LongSupplier clock;
        private Long stoppedAt;
        private Long restartedAt;

        StampingReplication(Replication replication, LongSupplier clock) {
            this.replication = replication;
            this.clock = clock;
        }

        @Override
        public synchronized void stopReplica() {
            replication.stopReplica();
            stoppedAt = clock.getAsLong();
        }

        @Override
        public synchronized void startReplica() {
            restartedAt = clock.getAsLong();
            replication.startReplica();
        }

        @Override
        public void awaitCaughtUp() {
            replication.awaitCaughtUp();
        }

        synchronized StaleWindow window() {
            if (stoppedAt == null || restartedAt == null) {
                throw new IllegalStateException("phase A did not both stop and restart replication");
            }
            return new StaleWindow(stoppedAt, restartedAt);
        }
    }
}
