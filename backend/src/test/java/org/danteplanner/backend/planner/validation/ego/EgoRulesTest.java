package org.danteplanner.backend.planner.validation.ego;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EgoRulesTest {

    private record KnownEgos(Map<String, Integer> ceilings, List<String> withoutCeiling) implements EgoReferenceData {

        @Override
        public boolean hasEgo(String egoId) {
            return ceilings.containsKey(egoId) || withoutCeiling.contains(egoId);
        }

        @Override
        public boolean belongsToSinner(String sinnerKey, String entityId) {
            return Integer.parseInt(entityId.substring(1, 3)) == Integer.parseInt(sinnerKey);
        }

        @Override
        public Optional<Integer> maxThreadspin(String egoId) {
            return Optional.ofNullable(ceilings.get(egoId));
        }
    }

    private static final EgoReferenceData DATA =
            new KnownEgos(Map.of("20501", 4, "20601", 4, "20503", 5), List.of("20502"));
    private static final ContentPath EGOS = ContentPath.of("equipment").key("5").field("egos");
    private static final ContentPath ZAYIN = EGOS.field("ZAYIN");

    @Test
    void check_WhenEquipmentHasNoSinners_ReportsNothing() {
        assertThat(EgoRules.check(Map.of(), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheZayinIsKnownAndWithinItsCeiling_ReportsNothing() {
        assertThat(EgoRules.check(sinnerFive(Map.of("ZAYIN", new Ego("20501", 4))), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheEgoIsUnknown_ReportsItAndSkipsTheThreadspin() {
        assertThat(EgoRules.check(sinnerFive(Map.of("ZAYIN", new Ego("20599", 9))), DATA))
                .containsExactly(new Violation(ErrorCode.UNKNOWN_EGO, ZAYIN.field("id"),
                        "EGO ID '20599' not found or invalid"));
    }

    @Test
    void check_WhenTheEgoBelongsToAnotherSinner_ReportsTheMismatch() {
        assertThat(EgoRules.check(sinnerFive(Map.of("ZAYIN", new Ego("20601", 4))), DATA))
                .containsExactly(new Violation(ErrorCode.MISMATCHED_EGO, ZAYIN.field("id"),
                        "EGO '20601' does not belong to sinner 5"));
    }

    @Test
    void check_WhenThreadspinIsOutsideTheGameRange_ReportsTheGameRange() {
        assertThat(EgoRules.check(sinnerFive(Map.of("ZAYIN", new Ego("20503", 9))), DATA))
                .containsExactly(new Violation(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN, ZAYIN.field("threadspin"),
                        "threadspin value 9 is out of range [1-5]"));
    }

    @Test
    void check_WhenThreadspinIsAboveTheEgosCeiling_ReportsTheCeiling() {
        assertThat(EgoRules.check(sinnerFive(Map.of("ZAYIN", new Ego("20501", 5))), DATA))
                .containsExactly(new Violation(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN, ZAYIN.field("threadspin"),
                        "threadspin for EGO 20501 value 5 is out of range [1-4]"));
    }

    @Test
    void check_WhenAnOptionalEgoHasNoCeiling_ReportsTheMissingCeiling() {
        assertThat(EgoRules.check(sinnerFive(Map.of("ZAYIN", new Ego("20501", 4), "TETH", new Ego("20502", 3))),
                DATA)).containsExactly(new Violation(ErrorCode.MISSING_EGO_THREADSPIN_CEILING,
                EGOS.field("TETH").field("threadspin"), "EGO '20502' has no threadspin ceiling in game data"));
    }

    private static Map<String, Map<String, Ego>> sinnerFive(Map<String, Ego> egos) {
        return Map.of("5", egos);
    }
}
