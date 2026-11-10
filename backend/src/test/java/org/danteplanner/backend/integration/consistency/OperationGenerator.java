package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.danteplanner.backend.integration.consistency.Plan.CutOregon;
import org.danteplanner.backend.integration.consistency.Plan.Latency;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;
import org.danteplanner.backend.integration.consistency.Plan.Stopped;

import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.ANONYMOUS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.AUTHOR_DEVICES;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.COMMENTER;
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
        CREATE,
        PUBLIC_READ,
        CATALOG,
        THREAD_READ
    }

    private static final List<Kind> AUTHOR_KINDS =
            List.of(Kind.SAVE, Kind.READ, Kind.LIST, Kind.PUBLISH, Kind.UNPUBLISH);
    private static final List<Kind> VIEWER_KINDS = List.of(Kind.READ, Kind.LIST);
    private static final List<Kind> CREATOR_KINDS =
            List.of(Kind.SAVE, Kind.READ, Kind.LIST, Kind.PUBLISH, Kind.UNPUBLISH, Kind.CREATE);
    private static final List<Kind> ANONYMOUS_KINDS =
            List.of(Kind.PUBLIC_READ, Kind.PUBLIC_READ, Kind.CATALOG, Kind.THREAD_READ);
    private static final int PHASES = 3;
    private static final KeyDraw P0 = KeyDraw.index(0);
    private static final KeyDraw P1 = KeyDraw.index(1);

    private OperationGenerator() {
    }

    public static Plan plan(long seed, int budget, long latencyBound) {
        return plan(seed, budget, latencyBound, true);
    }

    static Plan plan(long seed, int budget, long latencyBound, boolean probes) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Region> homes = homes(random);
        int phaseBudget = budget / PHASES;
        int phasePublicReads = PUBLIC_READ_BUDGET / PHASES;
        List<List<PlannedOperation>> phaseA = sessions(random, phaseBudget, phasePublicReads);
        Latency latency = new Latency(random.nextLong(latencyBound + 1));
        List<List<PlannedOperation>> phaseB = sessions(random, phaseBudget, phasePublicReads);
        List<List<PlannedOperation>> deleteRound = emptySessions();
        deleteRound.get(CREATOR).add(new PlannedOperation.Delete(draw(random)));
        List<List<PlannedOperation>> deactivateRound = emptySessions();
        deactivateRound.get(CREATOR).add(new PlannedOperation.Deactivate());
        List<List<PlannedOperation>> reactivateRound = emptySessions();
        reactivateRound.get(CREATOR).add(new PlannedOperation.Reactivate());
        List<List<PlannedOperation>> phaseC = sessions(random, budget - (PHASES - 1) * phaseBudget,
                PUBLIC_READ_BUDGET - (PHASES - 1) * phasePublicReads);
        List<Round> roundsA = new ArrayList<>(List.of(new Round(phaseA)));
        roundsA.addAll(probes ? probeRoundsA() : List.of());
        roundsA.add(new Round(deactivateRound));
        List<Round> roundsB = new ArrayList<>(List.of(new Round(reactivateRound)));
        roundsB.addAll(probes ? probeRoundsB() : List.of());
        roundsB.addAll(List.of(new Round(deleteRound), new Round(phaseB)));
        return new Plan(homes, new Phase(roundsA, latency), new Phase(roundsB, new Stopped()),
                new Phase(List.of(new Round(phaseC)), new CutOregon()));
    }

    private static List<Round> probeRoundsA() {
        List<List<PlannedOperation>> publish = emptySessions();
        publish.get(CREATOR).addAll(List.of(new PlannedOperation.Publish(P0), new PlannedOperation.Publish(P1)));
        List<List<PlannedOperation>> unpublish = emptySessions();
        unpublish.get(CREATOR).add(new PlannedOperation.Unpublish(P1));
        return List.of(new Round(publish), commentRound(), everySession(new PlannedOperation.ThreadRead(P1)),
                settingsSaveRound(), settingsReadRound(), new Round(unpublish),
                everySession(new PlannedOperation.CatalogList()));
    }

    private static List<Round> probeRoundsB() {
        return List.of(everySession(new PlannedOperation.PublicRead(P0)), commentRound(),
                everySession(new PlannedOperation.ThreadRead(P1)), settingsSaveRound(), settingsReadRound());
    }

    private static Round commentRound() {
        List<List<PlannedOperation>> comment = emptySessions();
        comment.get(COMMENTER).add(new PlannedOperation.Comment(P1));
        return new Round(comment);
    }

    private static Round settingsSaveRound() {
        List<List<PlannedOperation>> save = emptySessions();
        save.get(CREATOR).add(new PlannedOperation.SettingsSave());
        return new Round(save);
    }

    private static Round settingsReadRound() {
        List<List<PlannedOperation>> reads = emptySessions();
        reads.subList(CREATOR, ANONYMOUS).forEach(operations -> operations.add(new PlannedOperation.SettingsRead()));
        return new Round(reads);
    }

    private static Round everySession(PlannedOperation operation) {
        List<List<PlannedOperation>> all = emptySessions();
        all.forEach(operations -> operations.add(operation));
        return new Round(all);
    }

    private static List<Region> homes(SplittableRandom random) {
        Region[] regions = Region.values();
        List<Region> homes = new ArrayList<>();
        for (int session = 0; session < SESSIONS.size(); session++) {
            homes.add(regions[random.nextInt(regions.length)]);
        }
        return homes;
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
            bySession.get(ANONYMOUS).add(operation(random,
                    ANONYMOUS_KINDS.get(random.nextInt(ANONYMOUS_KINDS.size()))));
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
            case PUBLIC_READ -> new PlannedOperation.PublicRead(draw(random));
            case CATALOG -> new PlannedOperation.CatalogList();
            case THREAD_READ -> new PlannedOperation.ThreadRead(draw(random));
        };
    }

    private static KeyDraw draw(SplittableRandom random) {
        return KeyDraw.share(random.nextDouble());
    }
}
