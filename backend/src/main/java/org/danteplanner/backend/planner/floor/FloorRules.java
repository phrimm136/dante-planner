package org.danteplanner.backend.planner.floor;

import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.danteplanner.backend.shared.util.GameConstants;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class FloorRules {

    private record Walk(List<FloorSelection> floors, int floorCount, List<Set<Integer>> allowedDifficulties) {}

    @FunctionalInterface
    private interface Rule {
        void check(Walk walk, List<Violation> violations);
    }

    private static final int UNSET_DIFFICULTY_IN_MESSAGE = -1;

    private static final Map<String, Rule> RULES = Map.of(
            "everyFloorPresent", FloorRules::everyFloorPresent,
            "themePackPresent", FloorRules::themePackPresent,
            "notRepeated", FloorRules::notRepeated,
            "sequence", FloorRules::sequence,
            "difficultyInRange", FloorRules::difficultyInRange,
            "noNormalAfterHard", FloorRules::noNormalAfterHard,
            "giftUnique", FloorRules::giftUnique);

    private final GameDataRegistry gameDataRegistry;

    public FloorRules(GameDataRegistry gameDataRegistry) {
        Stage.requireTableStages(gameDataRegistry.floorRules().stages());
        this.gameDataRegistry = gameDataRegistry;
    }

    public Admission admit(FloorBoundary.Parsed parsed, MDCategory category, Stage stage) {
        FloorRuleTable table = gameDataRegistry.floorRules();
        int floorCount = table.floorCount(category);
        if (parsed.floorCount() != floorCount) {
            throw new IllegalArgumentException("floors were parsed with floor count " + parsed.floorCount() + " but "
                    + category.getValue() + " has " + floorCount);
        }
        List<FloorSelection> passed = parsed.floors().stream()
                .<FloorSelection>mapMulti((floor, accepted) -> {
                    if (floor instanceof ParsedFloor.Accepted(FloorSelection selection)) {
                        accepted.accept(selection);
                    }
                })
                .toList();

        List<Rule> required = RULES.entrySet().stream()
                .filter(rule -> table.stagesFor(rule.getKey()).contains(stage.tableName()))
                .map(Map.Entry::getValue)
                .toList();
        if (required.isEmpty()) {
            return new Admission.Admitted(passed, parsed.violations());
        }
        if (!parsed.violations().isEmpty()) {
            return new Admission.Rejected(parsed.violations());
        }

        Walk walk = new Walk(passed, floorCount, IntStream.range(0, floorCount)
                .mapToObj(index -> table.allowedDifficulties(category, index))
                .toList());
        List<Violation> violations = new ArrayList<>();
        required.forEach(rule -> rule.check(walk, violations));
        if (violations.isEmpty()) {
            return new Admission.Admitted(passed, List.of());
        }
        violations.sort(Violation.BY_PATH_THEN_CODE);
        return new Admission.Rejected(violations);
    }

    private static void everyFloorPresent(Walk walk, List<Violation> violations) {
        for (int index = walk.floors().size(); index < walk.floorCount(); index++) {
            violations.add(Violation.floorMissingThemePack(FloorBoundary.floorPath(index)));
        }
    }

    private static void themePackPresent(Walk walk, List<Violation> violations) {
        for (int index = 0; index < walk.floors().size(); index++) {
            if (walk.floors().get(index).themePack() instanceof ThemePack.None) {
                violations.add(Violation.floorMissingThemePack(FloorBoundary.floorPath(index)));
            }
        }
    }

    private static void notRepeated(Walk walk, List<Violation> violations) {
        Map<String, Integer> firstFloorByThemePack = new HashMap<>();
        for (int index = 0; index < walk.floors().size(); index++) {
            if (!(walk.floors().get(index).themePack() instanceof ThemePack.Chosen(String id))) {
                continue;
            }
            Integer firstFloor = firstFloorByThemePack.putIfAbsent(id, index);
            if (firstFloor != null) {
                violations.add(Violation.floorDuplicateThemePack(
                        FloorBoundary.floorPath(index) + ".themePackId", id, firstFloor));
            }
        }
    }

    private static void sequence(Walk walk, List<Violation> violations) {
        for (int index = 1; index < walk.floors().size(); index++) {
            if (walk.floors().get(index).themePack() instanceof ThemePack.Chosen
                    && walk.floors().get(index - 1).themePack() instanceof ThemePack.None) {
                String path = FloorBoundary.floorPath(index);
                violations.add(Violation.invalidSequence(path,
                        path + " requires themePackId in " + FloorBoundary.floorPath(index - 1)));
            }
        }
    }

    private static void difficultyInRange(Walk walk, List<Violation> violations) {
        for (int index = 0; index < walk.floors().size(); index++) {
            Set<Integer> allowed = walk.allowedDifficulties().get(index);
            Difficulty difficulty = walk.floors().get(index).difficulty();
            if (difficulty instanceof Difficulty.Set(int value) && allowed.contains(value)) {
                continue;
            }
            String shown = switch (difficulty) {
                case Difficulty.Set(int value) -> String.valueOf(value);
                case Difficulty.OutOfRange(BigInteger value) -> value.toString();
                case Difficulty.Unset() -> String.valueOf(UNSET_DIFFICULTY_IN_MESSAGE);
            };
            violations.add(Violation.valueOutOfRange(FloorBoundary.floorPath(index) + ".difficulty",
                    shown, Collections.min(allowed), Collections.max(allowed)));
        }
    }

    private static void noNormalAfterHard(Walk walk, List<Violation> violations) {
        int firstHard = -1;
        for (int index = 0; index < walk.floors().size(); index++) {
            Difficulty difficulty = walk.floors().get(index).difficulty();
            if (firstHard < 0 && difficulty.equals(Difficulty.of(GameConstants.HARD_DIFFICULTY))) {
                firstHard = index;
                continue;
            }
            if (firstHard >= 0 && difficulty.equals(Difficulty.of(GameConstants.NORMAL_DIFFICULTY))
                    && walk.allowedDifficulties().get(index).contains(GameConstants.NORMAL_DIFFICULTY)) {
                String path = FloorBoundary.floorPath(index) + ".difficulty";
                violations.add(Violation.invalidSequence(path,
                        path + " is NORMAL after HARD in " + FloorBoundary.floorPath(firstHard) + ".difficulty"));
            }
        }
    }

    private static void giftUnique(Walk walk, List<Violation> violations) {
        for (int index = 0; index < walk.floors().size(); index++) {
            Set<String> seen = new HashSet<>();
            for (String giftId : walk.floors().get(index).giftIds()) {
                if (!seen.add(giftId)) {
                    violations.add(Violation.duplicateValue(FloorBoundary.floorPath(index) + ".giftIds", giftId));
                    break;
                }
            }
        }
    }
}
