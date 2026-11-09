package org.danteplanner.backend.integration.consistency;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageTest {

    @Test
    void coverage_WhenTheGateServesReplicaCounterNeverMoves_NamesItUncovered() throws Exception {
        Map<CoverageTarget, Double> counters = new EnumMap<>(CoverageTarget.class);
        Coverage coverage = new Coverage(target -> counters.getOrDefault(target, 0.0));

        coverage.measure(() -> moveAllBut(counters, CoverageTarget.GATE_SERVES_REPLICA));
        coverage.measure(() -> moveAllBut(counters, CoverageTarget.GATE_SERVES_REPLICA));

        assertThat(coverage.uncovered()).containsExactly("gate serves the replica");
        assertThat(coverage.summary()).isEqualTo("{gate pins the primary=2, gate serves the replica=0, "
                + "oregon read fell back=2, oregon breaker opened=2}");
    }

    @Test
    void coverage_WhenEveryCounterMovesInSomeSeed_HasNothingUncovered() throws Exception {
        Map<CoverageTarget, Double> counters = new EnumMap<>(CoverageTarget.class);
        counters.put(CoverageTarget.OREGON_BREAKER_OPENED, 7.0);
        Coverage coverage = new Coverage(target -> counters.getOrDefault(target, 0.0));

        coverage.measure(() -> moveAllBut(counters, CoverageTarget.OREGON_BREAKER_OPENED));
        coverage.measure(() -> counters.merge(CoverageTarget.OREGON_BREAKER_OPENED, 1.0, Double::sum));

        assertThat(coverage.uncovered()).isEmpty();
    }

    @Test
    void coverage_WhenNoSeedRan_NamesEveryTargetUncovered() {
        Coverage coverage = new Coverage(target -> 0.0);

        assertThat(coverage.uncovered()).containsExactly("gate pins the primary", "gate serves the replica",
                "oregon read fell back", "oregon breaker opened");
    }

    @Test
    void coverage_WhenTheOregonFallbackPoolNeverMoves_NamesItUncovered() throws Exception {
        Map<CoverageTarget, Double> counters = new EnumMap<>(CoverageTarget.class);
        Coverage coverage = new Coverage(target -> counters.getOrDefault(target, 0.0));

        coverage.measure(() -> moveAllBut(counters, CoverageTarget.OREGON_READ_FELL_BACK));

        assertThat(coverage.uncovered()).containsExactly("oregon read fell back");
    }

    @Test
    void uncoveredDescription_WhenTheCapIsSpentWithATargetUnhit_NamesTheExtraSeedCount() throws Exception {
        Map<CoverageTarget, Double> counters = new EnumMap<>(CoverageTarget.class);
        Coverage coverage = new Coverage(target -> counters.getOrDefault(target, 0.0));
        for (int seed = 0; seed < ConsistencyBudget.COVERAGE_EXTENSION_CAP; seed++) {
            coverage.measure(() -> moveAllBut(counters, CoverageTarget.GATE_SERVES_REPLICA));
        }

        assertThat(coverage.uncovered()).containsExactly("gate serves the replica");
        assertThat(ConsistencyBudget.uncoveredDescription(ConsistencyBudget.COVERAGE_EXTENSION_CAP, coverage))
                .contains("after 10 extra seeds (ADR 180)")
                .endsWith(coverage.summary());
    }

    private static Void moveAllBut(Map<CoverageTarget, Double> counters, CoverageTarget still) {
        for (CoverageTarget target : CoverageTarget.values()) {
            if (target != still) {
                counters.merge(target, 1.0, Double::sum);
            }
        }
        return null;
    }
}
