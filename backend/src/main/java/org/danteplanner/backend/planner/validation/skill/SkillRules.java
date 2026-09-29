package org.danteplanner.backend.planner.validation.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.JsonNumbers;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class SkillRules {

    static final int SKILL_EA_TOTAL = 6;

    private static final Set<String> SKILL_SLOTS = Set.of("0", "1", "2");
    private static final int NOTHING_COUNTED = 0;

    private SkillRules() {
    }

    public static List<Violation> check(List<SinnerEntry> skillEAState) {
        return skillEAState.stream()
                .flatMap(SkillRules::sinnerSkills)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> sinnerSkills(SinnerEntry sinner) {
        List<FieldResult<Integer>> slots = sinner.value().properties().stream()
                .map(slot -> slot(sinner.path(), slot.getKey(), slot.getValue()))
                .toList();
        int total = slots.stream().mapToInt(FieldResult::value).sum();
        return Stream.concat(
                slots.stream().flatMap(slot -> slot.violations().stream()),
                total == SKILL_EA_TOTAL ? Stream.empty() : Stream.of(SkillViolations.totalOutOfRange(sinner.path(), total)));
    }

    private static FieldResult<Integer> slot(ContentPath sinnerPath, String slotKey, JsonNode value) {
        ContentPath path = sinnerPath.key(slotKey);
        return SKILL_SLOTS.contains(slotKey)
                ? JsonNumbers.intValue(value)
                        .map(FieldResult::of)
                        .orElseGet(() -> FieldResult.rejected(NOTHING_COUNTED,
                                Violation.invalidFieldType(path, JsonKind.NUMBER, value)))
                : FieldResult.rejected(NOTHING_COUNTED, SkillViolations.unknownSlot(sinnerPath, slotKey));
    }
}
