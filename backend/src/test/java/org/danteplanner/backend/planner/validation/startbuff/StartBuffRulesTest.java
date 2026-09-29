package org.danteplanner.backend.planner.validation.startbuff;

import com.fasterxml.jackson.databind.node.MissingNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.danteplanner.backend.planner.validation.ContentFixtures.json;

class StartBuffRulesTest {

    private static final Optional<StartBuffSeason> SEASON =
            Optional.of(Set.of("100", "101", "115", "200", "201", "302")::contains);
    private static final ContentPath BUFFS = ContentPath.of("selectedBuffIds");

    @Test
    void check_WhenBuffsAreAbsent_ReportsNothing() {
        assertThat(StartBuffRules.check(MissingNode.getInstance(), SEASON)).isEmpty();
    }

    @Test
    void check_WhenBuffsAreKnownWithDistinctBases_ReportsNothing() {
        assertThat(StartBuffRules.check(json("[100,201,302]"), SEASON)).isEmpty();
    }

    @Test
    void check_WhenBuffsAreNotAnArray_ReportsTheirKind() {
        assertThat(StartBuffRules.check(json("\"none\""), SEASON)).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, BUFFS, "Field 'selectedBuffIds' must be array, got string \"none\""));
    }

    @Test
    void check_WhenTheSeasonIsUnknown_ReportsOnlyTheKind() {
        assertThat(StartBuffRules.check(json("[999,\"x\"]"), Optional.empty())).isEmpty();
        assertThat(StartBuffRules.check(json("{}"), Optional.empty())).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, BUFFS, "Field 'selectedBuffIds' must be array, got object"));
    }

    @Test
    void check_WhenThereAreMoreThanTenBuffs_ReportsTheCountBesideEachBuff() {
        assertThat(StartBuffRules.check(json("[100,101,102,103,104,105,106,107,108,109,\"x\"]"), SEASON))
                .containsExactly(
                        new Violation(ErrorCode.EXCESS_STARTBUFF, BUFFS,
                                "selectedBuffIds count value 11 is out of range [0-10]"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(2),
                                "selectedBuffIds ID '102' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(3),
                                "selectedBuffIds ID '103' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(4),
                                "selectedBuffIds ID '104' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(5),
                                "selectedBuffIds ID '105' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(6),
                                "selectedBuffIds ID '106' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(7),
                                "selectedBuffIds ID '107' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(8),
                                "selectedBuffIds ID '108' not found or invalid"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(9),
                                "selectedBuffIds ID '109' not found or invalid"),
                        new Violation(ErrorCode.INVALID_FIELD_TYPE, BUFFS.index(10),
                                "Field 'selectedBuffIds[10]' must be number, got string \"x\""));
    }

    @Test
    void check_WhenABuffIsNotAnInteger_ReportsItAtItsIndex() {
        assertThat(StartBuffRules.check(json("[100,\"x\",101.0]"), SEASON)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, BUFFS.index(1),
                        "Field 'selectedBuffIds[1]' must be number, got string \"x\""));
    }

    @Test
    void check_WhenABuffIsUnknown_ReportsItAndSkipsItsBase() {
        assertThat(StartBuffRules.check(json("[100,999]"), SEASON)).containsExactly(new Violation(
                ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(1), "selectedBuffIds ID '999' not found or invalid"));
    }

    @Test
    void check_WhenABaseIdIsOutOfRange_ReportsTheBase() {
        assertThat(StartBuffRules.check(json("[115]"), SEASON)).containsExactly(new Violation(
                ErrorCode.OUT_OF_RANGE_STARTBUFF, BUFFS.index(0), "selectedBuffIds[0] base ID value 15 is out of range [0-9]"));
    }

    @Test
    void check_WhenTwoBuffsShareABase_ReportsTheLaterOne() {
        assertThat(StartBuffRules.check(json("[100,200,101]"), SEASON)).containsExactly(new Violation(
                ErrorCode.DUPLICATE_STARTBUFF, BUFFS.index(1), "Duplicate value '0' in selectedBuffIds base IDs"));
    }

    @Test
    void check_WhenElevenBuffsRepeatABase_ReportsTheCountAndTheRepeat() {
        assertThat(StartBuffRules.check(json("[100,101,115,201,302,100,101,115,201,302,200]"), SEASON))
                .extracting(Violation::code, violation -> violation.path().render())
                .contains(tuple(ErrorCode.EXCESS_STARTBUFF, "selectedBuffIds"),
                        tuple(ErrorCode.DUPLICATE_STARTBUFF, "selectedBuffIds[10]"));
    }

    @Test
    void check_WhenABuffIsAnIntegralValuedFraction_AcceptsItAsItsInteger() {
        assertThat(StartBuffRules.check(json("[100.0,201]"), SEASON)).isEmpty();
    }
}
