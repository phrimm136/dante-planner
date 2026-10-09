package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.danteplanner.backend.integration.consistency.Plan.Latency;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;
import org.danteplanner.backend.integration.consistency.Plan.Stopped;

import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.ANONYMOUS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.AUTHOR_DEVICES;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.CREATOR;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.PUBLIC_READ_BUDGET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.VIEWER;

public final class OperationGenerator {

    private enum Kind {
        SAVE,
        READ,
        LIST,
        PUBLISH,
        UNPUBLISH,
        CREATE
    }

    private static final List<Kind> AUTHOR_KINDS =
            List.of(Kind.SAVE, Kind.READ, Kind.LIST, Kind.PUBLISH, Kind.UNPUBLISH);
    private static final List<Kind> VIEWER_KINDS = List.of(Kind.READ, Kind.LIST);
    private static final List<Kind> CREATOR_KINDS =
            List.of(Kind.SAVE, Kind.READ, Kind.LIST, Kind.PUBLISH, Kind.UNPUBLISH, Kind.CREATE);
    private static final int PHASES = 2;

    private OperationGenerator() {
    }

    public static Plan plan(long seed, int budget, long latencyBound) {
        SplittableRandom random = new SplittableRandom(seed);
        int phaseABudget = budget / PHASES;
        int phaseAPublicReads = PUBLIC_READ_BUDGET / PHASES;
        List<List<PlannedOperation>> phaseA = sessions(random, phaseABudget, phaseAPublicReads);
        Latency latency = new Latency(random.nextLong(latencyBound + 1));
        List<List<PlannedOperation>> phaseB =
                sessions(random, budget - phaseABudget, PUBLIC_READ_BUDGET - phaseAPublicReads);
        List<List<PlannedOperation>> deleteRound = emptySessions();
        deleteRound.get(CREATOR).add(new PlannedOperation.Delete(draw(random)));
        return new Plan(new Phase(List.of(new Round(phaseA)), latency),
                new Phase(List.of(new Round(deleteRound), new Round(phaseB)), new Stopped()));
    }

    private static List<List<PlannedOperation>> sessions(SplittableRandom random, int authorBudget,
            int publicReads) {
        List<List<PlannedOperation>> bySession = emptySessions();
        for (int step = 0; step < authorBudget; step++) {
            int session = step % AUTHOR_DEVICES;
            List<Kind> kinds = kindsOf(session);
            bySession.get(session).add(operation(random, kinds.get(random.nextInt(kinds.size()))));
        }
        for (int read = 0; read < publicReads; read++) {
            bySession.get(ANONYMOUS).add(new PlannedOperation.PublicRead(draw(random)));
        }
        return bySession;
    }

    private static List<List<PlannedOperation>> emptySessions() {
        List<List<PlannedOperation>> bySession = new ArrayList<>();
        for (int session = 0; session < SESSIONS.size(); session++) {
            bySession.add(new ArrayList<>());
        }
        return bySession;
    }

    private static List<Kind> kindsOf(int session) {
        if (session == CREATOR) {
            return CREATOR_KINDS;
        }
        return session == VIEWER ? VIEWER_KINDS : AUTHOR_KINDS;
    }

    private static PlannedOperation operation(SplittableRandom random, Kind kind) {
        return switch (kind) {
            case SAVE -> new PlannedOperation.Save(draw(random));
            case READ -> new PlannedOperation.Read(draw(random));
            case LIST -> new PlannedOperation.ListAll();
            case PUBLISH -> new PlannedOperation.Publish(draw(random));
            case UNPUBLISH -> new PlannedOperation.Unpublish(draw(random));
            case CREATE -> new PlannedOperation.Create();
        };
    }

    private static KeyDraw draw(SplittableRandom random) {
        return new KeyDraw(random.nextDouble());
    }
}
