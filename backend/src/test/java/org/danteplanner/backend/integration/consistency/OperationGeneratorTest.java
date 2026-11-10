package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.integration.consistency.Plan.CutOregon;
import org.danteplanner.backend.integration.consistency.Plan.Latency;
import org.danteplanner.backend.integration.consistency.Plan.Phase;
import org.danteplanner.backend.integration.consistency.Plan.Round;
import org.danteplanner.backend.integration.consistency.Plan.Stopped;

import static org.assertj.core.api.Assertions.assertThat;

class OperationGeneratorTest {

    private static final int BUDGET = 100;
    private static final long LATENCY_BOUND = 80;
    private static final int S1 = 0;
    private static final int VIEWER = 3;
    private static final int ANON = 4;
    private static final int SESSIONS = 5;
    private static final int PROBE_ROUNDS_A = 7;
    private static final int PROBE_ROUNDS_B = 5;
    private static final int PROBE_AUTHOR_OPERATIONS = 31;
    private static final KeyDraw P0 = KeyDraw.index(0);
    private static final KeyDraw P1 = KeyDraw.index(1);

    @Test
    void plan_WhenBuiltTwiceFromOneSeedBudgetAndBound_IsEqual() {
        Plan first = OperationGenerator.plan(42, BUDGET, LATENCY_BOUND);
        Plan second = OperationGenerator.plan(42, BUDGET, LATENCY_BOUND);

        assertThat(second).isEqualTo(first);
        assertThat(second.homes()).isEqualTo(first.homes()).hasSize(SESSIONS);
        assertThat(authorOperations(first.phaseA()) + authorOperations(first.phaseB())
                + authorOperations(first.phaseC()))
                .as("author operations, plus phase B's delete, the deactivation, the reactivation and two sets of "
                        + "probe rounds of phases A and B")
                .isEqualTo(BUDGET + 3 + PROBE_AUTHOR_OPERATIONS);
    }

    @Test
    void plan_WhenSeedsDiffer_Differs() {
        assertThat(OperationGenerator.plan(1, BUDGET, LATENCY_BOUND))
                .isNotEqualTo(OperationGenerator.plan(2, BUDGET, LATENCY_BOUND));
    }

    @Test
    void sessions_WhenSeedsRunFromOneToFifty_DrawOnlyTheirOwnKindsAndEveryAuthorKindAppears() {
        Set<Class<?>> authorKinds = new HashSet<>();
        Set<Class<?>> anonymousKinds = new HashSet<>();
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND, false);
            for (Phase phase : List.of(plan.phaseA(), plan.phaseB(), plan.phaseC())) {
                List<List<PlannedOperation>> bySession = bySession(phase);
                assertThat(bySession.get(ANON)).as("seed %d anon", seed)
                        .isNotEmpty()
                        .allMatch(OperationGeneratorTest::anonymous);
                assertThat(bySession.subList(S1, ANON)).as("seed %d authors", seed)
                        .allSatisfy(operations -> assertThat(operations)
                                .noneMatch(OperationGeneratorTest::anonymous));
                bySession.get(ANON).forEach(operation -> anonymousKinds.add(operation.getClass()));
                assertThat(bySession.get(VIEWER)).as("seed %d viewer", seed)
                        .isNotEmpty()
                        .allMatch(operation -> operation instanceof PlannedOperation.Read
                                || operation instanceof PlannedOperation.ListAll);
                assertThat(bySession.subList(S1 + 1, ANON)).as("seed %d s2, s3 and viewer", seed)
                        .allSatisfy(operations -> assertThat(operations)
                                .noneMatch(PlannedOperation.Create.class::isInstance)
                                .noneMatch(PlannedOperation.Delete.class::isInstance));
                bySession.subList(S1, ANON).forEach(operations ->
                        operations.forEach(operation -> authorKinds.add(operation.getClass())));
            }
        });

        assertThat(authorKinds).contains(PlannedOperation.Save.class, PlannedOperation.Read.class,
                PlannedOperation.ListAll.class, PlannedOperation.Publish.class, PlannedOperation.Unpublish.class,
                PlannedOperation.Create.class);
        assertThat(anonymousKinds).containsExactlyInAnyOrder(PlannedOperation.PublicRead.class,
                PlannedOperation.CatalogList.class, PlannedOperation.ThreadRead.class);
    }

    @Test
    void faults_WhenSeedsRunFromOneToFifty_AreALatencyUpToTheBoundThenAStopThenAnOregonCut() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);

            assertThat(plan.phaseA().fault()).as("seed %d phase A", seed)
                    .isInstanceOfSatisfying(Latency.class,
                            latency -> assertThat(latency.millis()).isBetween(0L, LATENCY_BOUND));
            assertThat(plan.phaseB().fault()).as("seed %d phase B", seed).isEqualTo(new Stopped());
            assertThat(plan.phaseC().fault()).as("seed %d phase C", seed).isEqualTo(new CutOregon());
        });
    }

    @Test
    void homes_WhenSeedsRunFromOneToFifty_GiveEverySessionSeoulOrOregonAndDrawBoth() {
        Set<Region> drawn = new HashSet<>();
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            List<Region> homes = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND).homes();

            assertThat(homes).as("seed %d homes", seed).hasSize(SESSIONS)
                    .allMatch(home -> home == Region.SEOUL || home == Region.OREGON);
            drawn.addAll(homes);
        });

        assertThat(drawn).containsExactlyInAnyOrder(Region.SEOUL, Region.OREGON);
    }

    @Test
    void phaseB_WhenSeedsRunFromOneToFifty_OpensWithAnS1ReactivationAndTheProbesThenAnS1Delete() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);

            List<Round> rounds = plan.phaseB().rounds();

            assertThat(rounds).as("seed %d phase B", seed).hasSize(3 + PROBE_ROUNDS_B);
            assertSoleS1Operation(rounds.get(0), PlannedOperation.Reactivate.class, seed);
            assertSoleS1Operation(rounds.get(1 + PROBE_ROUNDS_B), PlannedOperation.Delete.class, seed);
            assertThat(rounds.get(2 + PROBE_ROUNDS_B).bySession().stream().flatMap(List::stream))
                    .as("seed %d random round", seed)
                    .noneMatch(PlannedOperation.Delete.class::isInstance)
                    .noneMatch(OperationGeneratorTest::accountChange);
            assertThat(bySession(plan.phaseA()).stream().flatMap(List::stream)).as("seed %d phase A", seed)
                    .noneMatch(PlannedOperation.Delete.class::isInstance);
            assertThat(bySession(plan.phaseC()).stream().flatMap(List::stream)).as("seed %d phase C", seed)
                    .noneMatch(PlannedOperation.Delete.class::isInstance)
                    .noneMatch(OperationGeneratorTest::accountChange);
        });
    }

    @Test
    void phaseA_WhenSeedsRunFromOneToFifty_ClosesWithARoundOfExactlyOneS1Deactivation() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            List<Round> rounds = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND).phaseA().rounds();

            assertThat(rounds).as("seed %d phase A", seed).hasSize(2 + PROBE_ROUNDS_A);
            assertThat(rounds.get(0).bySession().stream().flatMap(List::stream)).as("seed %d first round", seed)
                    .noneMatch(OperationGeneratorTest::accountChange);
            assertSoleS1Operation(rounds.get(1 + PROBE_ROUNDS_A), PlannedOperation.Deactivate.class, seed);
        });
    }

    @Test
    void probeRounds_WhenSeedsRunFromOneToFifty_FollowPhaseAsOperationsAndPhaseBsOperationsInOrder() {
        Round comment = round(List.of(), List.of(new PlannedOperation.Comment(P1)), List.of(), List.of(), List.of());
        Round threads = everySession(new PlannedOperation.ThreadRead(P1));
        Round settingsSave = round(List.of(new PlannedOperation.SettingsSave()), List.of(), List.of(), List.of(),
                List.of());
        Round settingsReads = round(List.of(new PlannedOperation.SettingsRead()),
                List.of(new PlannedOperation.SettingsRead()), List.of(new PlannedOperation.SettingsRead()),
                List.of(new PlannedOperation.SettingsRead()), List.of());
        List<Round> probesA = List.of(
                round(List.of(new PlannedOperation.Publish(P0), new PlannedOperation.Publish(P1)), List.of(),
                        List.of(), List.of(), List.of()),
                comment, threads, settingsSave, settingsReads,
                round(List.of(new PlannedOperation.Unpublish(P1)), List.of(), List.of(), List.of(), List.of()),
                everySession(new PlannedOperation.CatalogList()));
        List<Round> probesB = List.of(everySession(new PlannedOperation.PublicRead(P0)), comment, threads,
                settingsSave, settingsReads);
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);

            assertThat(plan.phaseA().rounds().subList(1, 1 + PROBE_ROUNDS_A)).as("seed %d phase A", seed)
                    .isEqualTo(probesA);
            assertThat(plan.phaseB().rounds().subList(1, 1 + PROBE_ROUNDS_B)).as("seed %d phase B", seed)
                    .isEqualTo(probesB);
        });
    }

    @Test
    void plan_WhenTheProbeRoundsAreRemoved_EqualsThePlanBuiltWithoutThem() {
        for (long seed : List.of(1L, 2L, 3L, 42L)) {
            Plan probed = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);
            Plan random = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND, false);
            List<Round> roundsA = new ArrayList<>(probed.phaseA().rounds());
            roundsA.subList(1, 1 + PROBE_ROUNDS_A).clear();
            List<Round> roundsB = new ArrayList<>(probed.phaseB().rounds());
            roundsB.subList(1, 1 + PROBE_ROUNDS_B).clear();

            assertThat(new Plan(probed.homes(), new Phase(roundsA, probed.phaseA().fault()),
                    new Phase(roundsB, probed.phaseB().fault()), probed.phaseC()))
                    .as("seed %d", seed)
                    .isEqualTo(random);
        }
    }

    private static Round everySession(PlannedOperation operation) {
        return round(List.of(operation), List.of(operation), List.of(operation), List.of(operation),
                List.of(operation));
    }

    @SafeVarargs
    private static Round round(List<PlannedOperation>... bySession) {
        return new Round(List.of(bySession));
    }

    private static void assertSoleS1Operation(Round round, Class<?> kind, long seed) {
        assertThat(round.bySession().get(S1)).as("seed %d %s round, s1", seed, kind.getSimpleName())
                .singleElement().isInstanceOf(kind);
        assertThat(round.bySession().subList(S1 + 1, round.bySession().size()))
                .as("seed %d %s round, other sessions", seed, kind.getSimpleName())
                .allSatisfy(operations -> assertThat(operations).isEmpty());
    }

    private static boolean anonymous(PlannedOperation operation) {
        return operation instanceof PlannedOperation.PublicRead
                || operation instanceof PlannedOperation.CatalogList
                || operation instanceof PlannedOperation.ThreadRead;
    }

    private static boolean accountChange(PlannedOperation operation) {
        return operation instanceof PlannedOperation.Deactivate || operation instanceof PlannedOperation.Reactivate;
    }

    private static int authorOperations(Phase phase) {
        return bySession(phase).subList(S1, ANON).stream().mapToInt(List::size).sum();
    }

    private static List<List<PlannedOperation>> bySession(Phase phase) {
        return IntStream.range(0, ANON + 1)
                .mapToObj(session -> phase.rounds().stream()
                        .flatMap(round -> round.bySession().get(session).stream())
                        .toList())
                .toList();
    }
}
