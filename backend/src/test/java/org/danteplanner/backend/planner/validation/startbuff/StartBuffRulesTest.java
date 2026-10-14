package org.danteplanner.backend.planner.validation.startbuff;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StartBuffRulesTest {

    private static final StartBuffSeason SEASON = () -> Set.of(
            "100", "101", "102", "103", "104", "105", "106", "107", "108", "109", "200", "201", "302");
    private static final StartBuffSeason SEASON_WITH_BASE_10 = () -> Set.of(
            "100", "101", "102", "103", "104", "105", "106", "107", "108", "109", "110");
    private static final ContentPath BUFFS = ContentPath.of("selectedBuffIds");

    @Test
    void check_WhenBuffsAreEmpty_ReportsNothing() {
        assertThat(StartBuffRules.check(List.of(), SEASON)).isEmpty();
    }

    @Test
    void check_WhenBuffsAreKnownWithDistinctBases_ReportsNothing() {
        assertThat(StartBuffRules.check(List.of(100L, 201L, 302L), SEASON)).isEmpty();
    }

    @Test
    void check_WhenThereAreMoreBuffsThanTheSeasonHasBases_ReportsTheCountBesideEachBuff() {
        assertThat(StartBuffRules.check(List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L),
                SEASON))
                .containsExactly(
                        new Violation(ErrorCode.EXCESS_STARTBUFF, BUFFS,
                                "selectedBuffIds count value 11 is out of range [0-10]"),
                        new Violation(ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(10),
                                "selectedBuffIds ID '110' not found or invalid"));
    }

    @Test
    void check_WhenTheSeasonHasBase10_AcceptsBuff110AndElevenBuffs() {
        assertThat(StartBuffRules.check(List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L),
                SEASON_WITH_BASE_10)).isEmpty();
    }

    @Test
    void check_WhenTheSeasonHasBase10AndTwelveBuffsArrive_BoundsTheCountByEleven() {
        assertThat(StartBuffRules.check(
                List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L, 100L), SEASON_WITH_BASE_10))
                .extracting(Violation::code, Violation::message)
                .contains(tuple(ErrorCode.EXCESS_STARTBUFF, "selectedBuffIds count value 12 is out of range [0-11]"));
    }

    @Test
    void check_WhenABuffIsUnknown_ReportsItAndSkipsItsBase() {
        assertThat(StartBuffRules.check(List.of(100L, 999L), SEASON)).containsExactly(new Violation(
                ErrorCode.UNKNOWN_STARTBUFF, BUFFS.index(1), "selectedBuffIds ID '999' not found or invalid"));
    }

    @Test
    void check_WhenTwoBuffsShareABase_ReportsTheLaterOne() {
        assertThat(StartBuffRules.check(List.of(100L, 200L, 101L), SEASON)).containsExactly(new Violation(
                ErrorCode.DUPLICATE_STARTBUFF, BUFFS.index(1), "Duplicate value '0' in selectedBuffIds base IDs"));
    }

    @Test
    void check_WhenElevenBuffsRepeatABase_ReportsTheCountAndTheRepeat() {
        assertThat(StartBuffRules.check(List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 200L),
                SEASON))
                .extracting(Violation::code, violation -> violation.path().render())
                .contains(tuple(ErrorCode.EXCESS_STARTBUFF, "selectedBuffIds"),
                        tuple(ErrorCode.DUPLICATE_STARTBUFF, "selectedBuffIds[10]"));
    }
}
