package org.danteplanner.backend.integration.consistency;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.integration.consistency.Plan.FaultWindow;

import static org.assertj.core.api.Assertions.assertThat;

class OperationGeneratorTest {

    private static final int BUDGET = 100;
    private static final int S1_PHASE_A_OPERATIONS = 34;
    private static final int FIRST_QUARTER_END = 8;
    private static final int LAST_QUARTER_START = 26;
    private static final int S1_PHASE_B_OPERATIONS = 4;

    @Test
    void plan_WhenBuiltTwiceFromOneSeed_IsEqualOperationByOperationAndFaultByFault() {
        Plan first = OperationGenerator.plan(42, BUDGET);
        Plan second = OperationGenerator.plan(42, BUDGET);

        assertThat(second.phaseA().rounds()).isEqualTo(first.phaseA().rounds());
        assertThat(second.phaseB().rounds()).isEqualTo(first.phaseB().rounds());
        assertThat(second.phaseA().fault()).isEqualTo(first.phaseA().fault());
        assertThat(second.phaseB().fault()).isEqualTo(first.phaseB().fault());
        assertThat(first.phaseA().rounds().get(0).bySession().stream().mapToInt(List::size).sum())
                .isEqualTo(BUDGET);
    }

    @Test
    void plan_WhenSeedsDiffer_Differs() {
        assertThat(OperationGenerator.plan(1, BUDGET)).isNotEqualTo(OperationGenerator.plan(2, BUDGET));
    }

    @Test
    void phaseAFault_WhenSeedsRunFromOneToFifty_StopsInTheFirstQuarterAndStartsInTheLastQuarter() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET);
            FaultWindow fault = plan.phaseA().fault();

            assertThat(plan.phaseA().rounds().get(0).bySession().get(0)).hasSize(S1_PHASE_A_OPERATIONS);
            assertThat(fault.stopAt()).as("seed %d stopAt", seed).isBetween(0, FIRST_QUARTER_END - 1);
            assertThat(fault.startAt()).as("seed %d startAt", seed)
                    .isBetween(LAST_QUARTER_START, S1_PHASE_A_OPERATIONS);
            assertThat(fault.startAt() - fault.stopAt()).as("seed %d window", seed)
                    .isGreaterThanOrEqualTo(S1_PHASE_A_OPERATIONS / 2);
        });
    }

    @Test
    void phaseA_WhenSeedsRunFromOneToFifty_LeavesK3ToS1AndPlantsASaveThenReadOfK3AtTheStop() {
        LongStream.rangeClosed(1, 50).forEach(seed -> {
            Plan plan = OperationGenerator.plan(seed, BUDGET);
            List<List<PlannedOperation>> bySession = plan.phaseA().rounds().get(0).bySession();
            int stopAt = plan.phaseA().fault().stopAt();

            assertThat(bySession.subList(1, bySession.size()))
                    .as("seed %d, s2 and s3", seed)
                    .allSatisfy(operations -> assertThat(operations)
                            .doesNotContain(new PlannedOperation.Save(Slot.K3)));
            assertThat(bySession.get(0).subList(stopAt, stopAt + 2))
                    .as("seed %d, s1 at stopAt %d", seed, stopAt)
                    .containsExactly(new PlannedOperation.Save(Slot.K3), new PlannedOperation.Read(Slot.K3));
        });
    }

    @Test
    void phaseBFault_WhenSeedsRunFromOneToFifty_StopsBeforeRoundOneAndStartsAfterRoundTwo() {
        LongStream.rangeClosed(1, 50).forEach(seed ->
                assertThat(OperationGenerator.plan(seed, BUDGET).phaseB().fault())
                        .as("seed %d", seed)
                        .isEqualTo(new FaultWindow(0, S1_PHASE_B_OPERATIONS)));
    }
}
