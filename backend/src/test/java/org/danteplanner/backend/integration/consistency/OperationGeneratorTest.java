package org.danteplanner.backend.integration.consistency;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

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

    @Test
    void plan_WhenBuiltTwiceFromOneSeedBudgetAndBound_IsEqual() {
        Plan first = OperationGenerator.plan(42, BUDGET, LATENCY_BOUND);
        Plan second = OperationGenerator.plan(42, BUDGET, LATENCY_BOUND);

        assertThat(second).isEqualTo(first);
        assertThat(authorOperations(first.phaseA()) + authorOperations(first.phaseB()))
                .as("author operations, plus phase B's delete")
                .isEqualTo(BUDGET + 1);
    }

    @Test
    void plan_WhenSeedsDiffer_Differs() {
        assertThat(OperationGenerator.plan(1, BUDGET, LATENCY_BOUND))
                .isNotEqualTo(OperationGenerator.plan(2, BUDGET, LATENCY_BOUND));
    }

    @Test
    void sessions_WhenSeedsRunFromOneToFifty_DrawOnlyTheirOwnKindsAndEveryAuthorKindAppears() {
        Set<Class<?>> authorKinds = new HashSet<>();
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);
            for (Phase phase : List.of(plan.phaseA(), plan.phaseB())) {
                List<List<PlannedOperation>> bySession = bySession(phase);
                assertThat(bySession.get(ANON)).as("seed %d anon", seed)
                        .isNotEmpty()
                        .allMatch(PlannedOperation.PublicRead.class::isInstance);
                assertThat(bySession.subList(S1, ANON)).as("seed %d authors", seed)
                        .allSatisfy(operations -> assertThat(operations)
                                .noneMatch(PlannedOperation.PublicRead.class::isInstance));
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
    }

    @Test
    void faults_WhenSeedsRunFromOneToFifty_AreALatencyUpToTheBoundThenAStopAndNeverATimeout() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);

            assertThat(plan.phaseA().fault()).as("seed %d phase A", seed)
                    .isInstanceOfSatisfying(Latency.class,
                            latency -> assertThat(latency.millis()).isBetween(0L, LATENCY_BOUND));
            assertThat(plan.phaseB().fault()).as("seed %d phase B", seed).isEqualTo(new Stopped());
        });
    }

    @Test
    void phaseB_WhenSeedsRunFromOneToFifty_OpensWithARoundOfExactlyOneS1Delete() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET, LATENCY_BOUND);

            List<Round> rounds = plan.phaseB().rounds();

            assertThat(rounds).as("seed %d phase B", seed).hasSize(2);
            assertThat(rounds.get(0).bySession().get(S1)).as("seed %d opening round, s1", seed)
                    .singleElement().isInstanceOf(PlannedOperation.Delete.class);
            assertThat(rounds.get(0).bySession().subList(S1 + 1, rounds.get(0).bySession().size()))
                    .as("seed %d opening round, other sessions", seed)
                    .allSatisfy(operations -> assertThat(operations).isEmpty());
            assertThat(rounds.get(1).bySession().stream().flatMap(List::stream)).as("seed %d second round", seed)
                    .noneMatch(PlannedOperation.Delete.class::isInstance);
            assertThat(bySession(plan.phaseA()).stream().flatMap(List::stream)).as("seed %d phase A", seed)
                    .noneMatch(PlannedOperation.Delete.class::isInstance);
        });
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
