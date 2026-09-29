package org.danteplanner.backend.planner.validation.skill;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SkillRules {

    static final int SKILL_EA_TOTAL = 6;

    private static final ContentPath SKILL_EA_STATE = ContentPath.of("skillEAState");

    private SkillRules() {
    }

    public static List<Violation> check(Map<String, SkillPlan> skillEAState) {
        return skillEAState.entrySet().stream()
                .flatMap(sinner -> total(SKILL_EA_STATE.key(sinner.getKey()), sinner.getValue()))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> total(ContentPath sinnerPath, SkillPlan plan) {
        long total = plan.first() + plan.second() + plan.third();
        return total == SKILL_EA_TOTAL
                ? Stream.empty()
                : Stream.of(SkillViolations.totalOutOfRange(sinnerPath, total));
    }
}
