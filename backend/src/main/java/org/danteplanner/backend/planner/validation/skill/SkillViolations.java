package org.danteplanner.backend.planner.validation.skill;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class SkillViolations {

    private SkillViolations() {
    }

    static Violation totalOutOfRange(ContentPath sinnerPath, long total) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_SKILL_TOTAL, sinnerPath,
                String.format("%s total", sinnerPath.render()), String.valueOf(total),
                SkillRules.SKILL_EA_TOTAL, SkillRules.SKILL_EA_TOTAL);
    }
}
