package org.danteplanner.backend.planner.validation.skill;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.skillsOfSinnerFive;

class SkillRulesTest {

    private static final ContentPath SINNER_FIVE = ContentPath.of("skillEAState").key("05");

    @Test
    void check_WhenSkillEAStateHasNoSinners_ReportsNothing() {
        assertThat(SkillRules.check(List.of())).isEmpty();
    }

    @Test
    void check_WhenSlotsAreIntegersSummingToSix_ReportsNothing() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":3,\"1\":2,\"2\":1}"))).isEmpty();
    }

    @Test
    void check_WhenTheTotalIsNotSix_ReportsTheTotal() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":1,\"1\":2,\"2\":1}"))).containsExactly(new Violation(
                ErrorCode.OUT_OF_RANGE_SKILL_TOTAL, SINNER_FIVE, "skillEAState[05] total value 4 is out of range [6-6]"));
    }

    @Test
    void check_WhenASlotIsUnknown_ReportsTheSlotAndTheTotalWithoutIt() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":3,\"1\":2,\"3\":1}"))).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_SKILL_TOTAL, SINNER_FIVE,
                        "skillEAState[05] total value 5 is out of range [6-6]"),
                new Violation(ErrorCode.INVALID_SKILL_SLOT, SINNER_FIVE.key("3"),
                        "Field 'skillEAState[05] slot '3'' has wrong type, expected 0, 1, or 2"));
    }

    @Test
    void check_WhenASlotIsAnIntegralValuedFraction_CountsItAsItsInteger() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":1.0,\"1\":2,\"2\":3}"))).isEmpty();
    }

    @Test
    void check_WhenAKnownSlotIsNotAnInteger_ReportsTheSlotAndNoTotal() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":\"3\",\"1\":2,\"2\":1}"))).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, SINNER_FIVE.key("0"),
                        "Field 'skillEAState[05][0]' must be number, got string \"3\""));
    }

    @Test
    void check_WhenTwoFractionsSumToSix_ReportsBothSlotsAndNoTotal() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":2.5,\"1\":2.5,\"2\":1}"))).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, SINNER_FIVE.key("0"),
                        "Field 'skillEAState[05][0]' must be number, got number 2.5"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, SINNER_FIVE.key("1"),
                        "Field 'skillEAState[05][1]' must be number, got number 2.5"));
    }

    @Test
    void check_WhenAnUnknownSlotHoldsAMalformedValue_ReportsTheSlotAndStillTheTotal() {
        assertThat(SkillRules.check(skillsOfSinnerFive("{\"0\":3,\"1\":2,\"3\":\"x\"}"))).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_SKILL_TOTAL, SINNER_FIVE,
                        "skillEAState[05] total value 5 is out of range [6-6]"),
                new Violation(ErrorCode.INVALID_SKILL_SLOT, SINNER_FIVE.key("3"),
                        "Field 'skillEAState[05] slot '3'' has wrong type, expected 0, 1, or 2"));
    }
}
