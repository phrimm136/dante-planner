package org.danteplanner.backend.integration.consistency;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageTest {

    @Test
    void coverage_WhenTheWithdrawalMarkerCounterNeverMoves_NamesItUncovered() throws Exception {
        Map<CoverageTarget, Double> counters = new EnumMap<>(CoverageTarget.class);
        Coverage coverage = new Coverage(target -> counters.getOrDefault(target, 0.0));

        coverage.measure(() -> moveAllBut(counters, CoverageTarget.WITHDRAWAL_MARKER_HIT));
        coverage.measure(() -> moveAllBut(counters, CoverageTarget.WITHDRAWAL_MARKER_HIT));

        assertThat(coverage.uncovered()).containsExactly("withdrawal-marker hit");
        assertThat(coverage.summary()).isEqualTo("{gate pins the primary=2, gate serves the replica=2, "
                + "replica miss promoted=2, tombstone hit=2, withdrawal-marker hit=0, oregon read fell back=2, "
                + "oregon breaker opened=2}");
    }

    @Test
    void coverage_WhenEveryCounterMovesInSomeSeed_HasNothingUncovered() throws Exception {
        Map<CoverageTarget, Double> counters = new EnumMap<>(CoverageTarget.class);
        counters.put(CoverageTarget.TOMBSTONE_HIT, 7.0);
        Coverage coverage = new Coverage(target -> counters.getOrDefault(target, 0.0));

        coverage.measure(() -> moveAllBut(counters, CoverageTarget.TOMBSTONE_HIT));
        coverage.measure(() -> counters.merge(CoverageTarget.TOMBSTONE_HIT, 1.0, Double::sum));

        assertThat(coverage.uncovered()).isEmpty();
    }

    @Test
    void coverage_WhenNoSeedRan_NamesEveryTargetUncovered() {
        Coverage coverage = new Coverage(target -> 0.0);

        assertThat(coverage.uncovered()).containsExactly("gate pins the primary", "gate serves the replica",
                "replica miss promoted", "tombstone hit", "withdrawal-marker hit", "oregon read fell back",
                "oregon breaker opened");
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
            coverage.measure(() -> moveAllBut(counters, CoverageTarget.WITHDRAWAL_MARKER_HIT));
        }

        assertThat(coverage.uncovered()).containsExactly("withdrawal-marker hit");
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
