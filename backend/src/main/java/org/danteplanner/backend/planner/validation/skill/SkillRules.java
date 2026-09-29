package org.danteplanner.backend.planner.validation.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.JsonNumbers;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class SkillRules {

    private sealed interface Slot permits Counted, UnknownSlot, MalformedValue {
    }

    private record Counted(int points) implements Slot {
    }

    private record UnknownSlot(Violation violation) implements Slot {
    }

    private record MalformedValue(Violation violation) implements Slot {
    }

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
        List<Slot> slots = sinner.value().properties().stream()
                .map(slot -> slot(sinner.path(), slot.getKey(), slot.getValue()))
                .toList();
        return Stream.concat(slots.stream().flatMap(slot -> violation(slot).stream()), total(sinner.path(), slots));
    }

    private static Stream<Violation> total(ContentPath sinnerPath, List<Slot> slots) {
        boolean malformed = slots.stream().anyMatch(MalformedValue.class::isInstance);
        int total = slots.stream().mapToInt(SkillRules::points).sum();
        return malformed || total == SKILL_EA_TOTAL
                ? Stream.empty()
                : Stream.of(SkillViolations.totalOutOfRange(sinnerPath, total));
    }

    private static Slot slot(ContentPath sinnerPath, String slotKey, JsonNode value) {
        return SKILL_SLOTS.contains(slotKey)
                ? JsonNumbers.intValue(value)
                        .<Slot>map(Counted::new)
                        .orElseGet(() -> new MalformedValue(
                                Violation.invalidFieldType(sinnerPath.key(slotKey), JsonKind.NUMBER, value)))
                : new UnknownSlot(SkillViolations.unknownSlot(sinnerPath, slotKey));
    }

    private static int points(Slot slot) {
        return switch (slot) {
            case Counted counted -> counted.points();
            case UnknownSlot unknown -> NOTHING_COUNTED;
            case MalformedValue malformed -> NOTHING_COUNTED;
        };
    }

    private static Optional<Violation> violation(Slot slot) {
        return switch (slot) {
            case Counted counted -> Optional.empty();
            case UnknownSlot unknown -> Optional.of(unknown.violation());
            case MalformedValue malformed -> Optional.of(malformed.violation());
        };
    }
}
