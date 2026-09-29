package org.danteplanner.backend.planner.validation.skill;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillRulesTest {

    private static final ContentPath SINNER_FIVE = ContentPath.of("skillEAState").key("5");

    @Test
    void check_WhenSkillEAStateHasNoSinners_ReportsNothing() {
        assertThat(SkillRules.check(Map.of())).isEmpty();
    }

    @Test
    void check_WhenSlotsSumToSix_ReportsNothing() {
        assertThat(SkillRules.check(Map.of("5", new SkillPlan(3, 2, 1)))).isEmpty();
    }

    @Test
    void check_WhenTheTotalIsNotSix_ReportsTheTotal() {
        assertThat(SkillRules.check(Map.of("5", new SkillPlan(1, 2, 1)))).containsExactly(new Violation(
                ErrorCode.OUT_OF_RANGE_SKILL_TOTAL, SINNER_FIVE, "skillEAState[5] total value 4 is out of range [6-6]"));
    }
}
