package org.danteplanner.backend.planner.validation.egogift;

import com.fasterxml.jackson.databind.node.MissingNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.json;

class EgoGiftRulesTest {

    private static final EgoGiftReferenceData DATA = Set.of("9001", "9002")::contains;
    private static final ContentPath OBSERVATION = ContentPath.of("observationGiftIds");
    private static final ContentPath COMPREHENSIVE = ContentPath.of("comprehensiveGiftIds");

    @Test
    void check_WhenBothGiftArraysAreAbsent_ReportsNothing() {
        assertThat(EgoGiftRules.check(MissingNode.getInstance(), MissingNode.getInstance(), DATA)).isEmpty();
    }

    @Test
    void check_WhenAGiftArrayIsNotAnArray_ReportsItsKind() {
        assertThat(EgoGiftRules.check(json("{}"), json("7"), DATA)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, COMPREHENSIVE,
                        "Field 'comprehensiveGiftIds' must be array, got number 7"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, OBSERVATION,
                        "Field 'observationGiftIds' must be array, got object"));
    }

    @Test
    void check_WhenAGiftIsNotAString_ReportsItAtItsIndex() {
        assertThat(EgoGiftRules.check(json("[9002]"), MissingNode.getInstance(), DATA)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, OBSERVATION.index(0),
                        "Field 'observationGiftIds[0]' must be string, got number 9002"));
    }

    @Test
    void check_WhenAGiftRepeatsTwice_ReportsEachRepeat() {
        assertThat(EgoGiftRules.check(json("[\"9002\",\"9002\",\"9002\"]"), MissingNode.getInstance(), DATA))
                .containsExactly(
                        new Violation(ErrorCode.DUPLICATE_EGOGIFT, OBSERVATION.index(1),
                                "Duplicate value '9002' in observationGiftIds"),
                        new Violation(ErrorCode.DUPLICATE_EGOGIFT, OBSERVATION.index(2),
                                "Duplicate value '9002' in observationGiftIds"));
    }

    @Test
    void check_WhenAGiftIsUnknown_ReportsItOncePerFirstOccurrence() {
        assertThat(EgoGiftRules.check(MissingNode.getInstance(), json("[\"9999\",\"9001\",\"9999\"]"), DATA))
                .containsExactly(
                        new Violation(ErrorCode.UNKNOWN_EGOGIFT, COMPREHENSIVE.index(0),
                                "comprehensiveGiftIds ID '9999' not found or invalid"),
                        new Violation(ErrorCode.DUPLICATE_EGOGIFT, COMPREHENSIVE.index(2),
                                "Duplicate value '9999' in comprehensiveGiftIds"));
    }
}
