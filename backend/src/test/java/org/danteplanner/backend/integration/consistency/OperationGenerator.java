package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.danteplanner.backend.integration.consistency.Plan.FaultWindow;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;

import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.AUTHOR_ONLY_SLOT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.FAULT_QUARTERS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.PERCENT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.READ_PERCENT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SAVE_PERCENT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SHARED_SLOTS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.STALE_READ_OFFSET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.STALE_SAVE_OFFSET;

public final class OperationGenerator {

    private static final Slot[] SLOTS = Slot.values();
    private static final Slot DELETED = Slot.K1;
    private static final int AUTHOR_SESSION = 0;

    private OperationGenerator() {
    }

    public static Plan plan(long seed, int budget) {
        SplittableRandom random = new SplittableRandom(seed);
        List<List<PlannedOperation>> bySession = emptySessions();
        for (int step = 0; step < budget; step++) {
            int session = step % SESSIONS;
            bySession.get(session).add(nextOperation(random, session == AUTHOR_SESSION));
        }
        List<PlannedOperation> author = bySession.get(AUTHOR_SESSION);
        FaultWindow phaseAFault = phaseAFault(random, author.size());
        plantStalePair(author, phaseAFault.stopAt());
        Phase phaseA = new Phase(List.of(new Round(bySession)), phaseAFault);
        List<Round> phaseBRounds = phaseBRounds();
        int phaseBFaultSessionOperations = phaseBRounds.stream()
                .mapToInt(round -> round.bySession().get(0).size())
                .sum();
        Phase phaseB = new Phase(phaseBRounds, new FaultWindow(0, phaseBFaultSessionOperations));
        return new Plan(phaseA, phaseB);
    }

    private static PlannedOperation nextOperation(SplittableRandom random, boolean author) {
        int draw = random.nextInt(PERCENT);
        if (draw < SAVE_PERCENT) {
            List<Slot> writable = author ? List.of(SLOTS) : SHARED_SLOTS;
            return new PlannedOperation.Save(writable.get(random.nextInt(writable.size())));
        }
        if (draw < SAVE_PERCENT + READ_PERCENT) {
            return new PlannedOperation.Read(SLOTS[random.nextInt(SLOTS.length)]);
        }
        return new PlannedOperation.ListAll();
    }

    private static void plantStalePair(List<PlannedOperation> author, int stopAt) {
        if (stopAt + STALE_READ_OFFSET < author.size()) {
            author.set(stopAt + STALE_SAVE_OFFSET, new PlannedOperation.Save(AUTHOR_ONLY_SLOT));
            author.set(stopAt + STALE_READ_OFFSET, new PlannedOperation.Read(AUTHOR_ONLY_SLOT));
        }
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
