package org.danteplanner.backend.planner.validation.skill;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class SkillViolations {

    private SkillViolations() {
    }

    static Violation unknownSlot(ContentPath sinnerPath, String slotKey) {
        return Violation.wrongType(ErrorCode.INVALID_SKILL_SLOT, sinnerPath.key(slotKey),
                String.format("%s slot '%s'", sinnerPath.render(), slotKey), "0, 1, or 2");
    }

    static Violation totalOutOfRange(ContentPath sinnerPath, int total) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_SKILL_TOTAL, sinnerPath,
                String.format("%s total", sinnerPath.render()), String.valueOf(total),
                SkillRules.SKILL_EA_TOTAL, SkillRules.SKILL_EA_TOTAL);
    }
}
