package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.danteplanner.backend.integration.consistency.Plan.FaultWindow;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;

import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.FAULT_QUARTERS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.PERCENT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.READ_PERCENT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SAVE_PERCENT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;

public final class OperationGenerator {

    private static final Slot[] SLOTS = Slot.values();
    private static final Slot DELETED = Slot.K1;

    private OperationGenerator() {
    }

    public static Plan plan(long seed, int budget) {
        SplittableRandom random = new SplittableRandom(seed);
        List<List<PlannedOperation>> bySession = emptySessions();
        for (int step = 0; step < budget; step++) {
            bySession.get(step % SESSIONS).add(nextOperation(random));
        }
        Phase phaseA = new Phase(List.of(new Round(bySession)), phaseAFault(random, bySession.get(0).size()));
        List<Round> phaseBRounds = phaseBRounds();
        int phaseBFaultSessionOperations = phaseBRounds.stream()
                .mapToInt(round -> round.bySession().get(0).size())
                .sum();
        Phase phaseB = new Phase(phaseBRounds, new FaultWindow(0, phaseBFaultSessionOperations));
        return new Plan(phaseA, phaseB);
    }

    private static PlannedOperation nextOperation(SplittableRandom random) {
        int draw = random.nextInt(PERCENT);
        Slot slot = SLOTS[random.nextInt(SLOTS.length)];
        if (draw < SAVE_PERCENT) {
            return new PlannedOperation.Save(slot);
        }
        if (draw < SAVE_PERCENT + READ_PERCENT) {
            return new PlannedOperation.Read(slot);
        }
        return new PlannedOperation.ListAll();
    }

    private static FaultWindow phaseAFault(SplittableRandom random, int faultSessionOperations) {
        int quarter = faultSessionOperations / FAULT_QUARTERS;
        int stopAt = random.nextInt(Math.max(1, quarter));
        int startAt = random.nextInt(faultSessionOperations - quarter, faultSessionOperations + 1);
        return new FaultWindow(stopAt, startAt);
    }

    private static List<Round> phaseBRounds() {
        List<List<PlannedOperation>> deleteRound = emptySessions();
        deleteRound.get(0).add(new PlannedOperation.Delete(DELETED));
        for (int session = 1; session < SESSIONS; session++) {
            deleteRound.get(session).add(new PlannedOperation.Read(DELETED));
        }
        List<List<PlannedOperation>> finalReads = emptySessions();
        for (List<PlannedOperation> operations : finalReads) {
            for (Slot slot : SLOTS) {
                operations.add(new PlannedOperation.Read(slot));
            }
        }
        return List.of(new Round(deleteRound), new Round(finalReads));
    }

    private static List<List<PlannedOperation>> emptySessions() {
        List<List<PlannedOperation>> bySession = new ArrayList<>();
        for (int session = 0; session < SESSIONS; session++) {
            bySession.add(new ArrayList<>());
        }
        return bySession;
    }
}
