package org.danteplanner.backend.planner.validation.egogift;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EgoGiftRulesTest {

    private static final EgoGiftReferenceData DATA = Set.of("9001", "9002")::contains;
    private static final ContentPath OBSERVATION = ContentPath.of("observationGiftIds");
    private static final ContentPath COMPREHENSIVE = ContentPath.of("comprehensiveGiftIds");

    @Test
    void check_WhenBothGiftListsAreEmpty_ReportsNothing() {
        assertThat(EgoGiftRules.check(List.of(), List.of(), DATA)).isEmpty();
    }

    @Test
    void check_WhenAGiftRepeatsTwice_ReportsEachRepeat() {
        assertThat(EgoGiftRules.check(List.of("9002", "9002", "9002"), List.of(), DATA))
                .containsExactly(
                        new Violation(ErrorCode.DUPLICATE_EGOGIFT, OBSERVATION.index(1),
                                "Duplicate value '9002' in observationGiftIds"),
                        new Violation(ErrorCode.DUPLICATE_EGOGIFT, OBSERVATION.index(2),
                                "Duplicate value '9002' in observationGiftIds"));
    }

    @Test
    void check_WhenAGiftIsUnknown_ReportsItOncePerFirstOccurrence() {
        assertThat(EgoGiftRules.check(List.of(), List.of("9999", "9001", "9999"), DATA))
                .containsExactly(
                        new Violation(ErrorCode.UNKNOWN_EGOGIFT, COMPREHENSIVE.index(0),
                                "comprehensiveGiftIds ID '9999' not found or invalid"),
                        new Violation(ErrorCode.DUPLICATE_EGOGIFT, COMPREHENSIVE.index(2),
                                "Duplicate value '9999' in comprehensiveGiftIds"));
    }

    @Test
    void check_WhenAListHoldsABaseAndItsEnhancedForm_ReportsNoRepeat() {
        EgoGiftReferenceData enhancedGifts = id -> id.endsWith("9001");

        assertThat(EgoGiftRules.check(List.of("9001", "19001"), List.of("19001", "29001"), enhancedGifts)).isEmpty();
    }
}
