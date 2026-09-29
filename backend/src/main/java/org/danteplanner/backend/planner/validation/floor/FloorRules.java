package org.danteplanner.backend.planner.validation.floor;

import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.FloorRuleTable.CategoryFloors;
import org.danteplanner.backend.planner.validation.FloorRuleTable.RuleName;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.util.GameConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class FloorRules {

    private record Walk(List<FloorSelection> floors, CategoryFloors layout) {

        ThemePack themePack(int index) {
            return floors.get(index).themePack();
        }

        Optional<String> chosenPack(int index) {
            return themePack(index).chosenId();
        }

        Difficulty difficulty(int index) {
            return floors.get(index).difficulty();
        }

        boolean isHard(int index) {
            return difficulty(index).equals(Difficulty.of(GameConstants.HARD_DIFFICULTY));
        }

        boolean isNormal(int index) {
            return difficulty(index).equals(Difficulty.of(GameConstants.NORMAL_DIFFICULTY));
        }

        List<String> giftIds(int index) {
            return floors.get(index).giftIds();
        }

        Set<Integer> allowed(int index) {
            return layout.difficulties().get(index);
        }

        int size() {
            return floors.size();
        }

        IntStream indices() {
            return IntStream.range(0, size());
        }
    }

    @FunctionalInterface
    private interface Rule {
        List<Violation> check(Walk walk);
    }

    private static final Map<Set<Integer>, ErrorCode> SINGLE_DIFFICULTY_CODES = Map.of(
            Set.of(GameConstants.HARD_DIFFICULTY), ErrorCode.OUT_OF_RANGE_DIFFICULTY_NOT_HARD,
            Set.of(GameConstants.EXTREME_DIFFICULTY), ErrorCode.OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME);

    private final Map<Stage, List<Rule>> requiredRules;

    @Autowired
    public FloorRules(GameDataRegistry gameDataRegistry) {
        this(gameDataRegistry.floorRules());
    }

    FloorRules(FloorRuleTable table) {
        Stage.requireTableStages(table.stages());
        this.requiredRules = Arrays.stream(Stage.values()).collect(Collectors.toMap(Function.identity(),
                stage -> requiredAt(table, stage), (first, later) -> first, () -> new EnumMap<>(Stage.class)));
    }

    public Admission admit(FloorBoundary.Parsed parsed, Stage stage) {
        List<Rule> required = requiredRules.get(stage);
        if (required.isEmpty()) {
            return admitAsStored(parsed);
        }
        if (!parsed.violations().isEmpty()) {
            return new Admission.Rejected(parsed.violations());
        }

        List<FloorSelection> accepted = parsed.accepted();
        Walk walk = new Walk(accepted, parsed.layout());
        List<Violation> violations = required.stream()
                .flatMap(rule -> rule.check(walk).stream())
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
        return violations.isEmpty()
                ? new Admission.Admitted(accepted, List.of())
                : new Admission.Rejected(violations);
    }

    private static Admission admitAsStored(FloorBoundary.Parsed parsed) {
        return new Admission.Admitted(parsed.indexed(), parsed.violations());
    }

    private static List<Rule> requiredAt(FloorRuleTable table, Stage stage) {
        return Arrays.stream(RuleName.values())
                .filter(name -> table.stagesFor(name).contains(stage.tableName()))
                .map(name -> rule(name, table.entryFloorCount()))
                .toList();
    }

    private static Rule rule(RuleName name, int entryFloorCount) {
        return switch (name) {
            case EVERY_FLOOR_PRESENT -> FloorRules::everyFloorPresent;
            case THEME_PACK_PRESENT -> FloorRules::themePackPresent;
            case NOT_REPEATED -> FloorRules::notRepeated;
            case SEQUENCE -> FloorRules::sequence;
            case DIFFICULTY_IN_RANGE -> walk -> difficultyInRange(walk, entryFloorCount);
            case NO_NORMAL_AFTER_HARD -> FloorRules::noNormalAfterHard;
            case GIFT_UNIQUE -> FloorRules::giftUnique;
        };
    }

    private static List<Violation> everyFloorPresent(Walk walk) {
        return IntStream.range(walk.floors().size(), walk.layout().floorCount())
                .mapToObj(index -> FloorViolations.missingFloor(FloorBoundary.floorPath(index)))
                .toList();
    }

    private static List<Violation> themePackPresent(Walk walk) {
        return walk.indices()
                .filter(index -> walk.themePack(index) instanceof ThemePack.None)
                .mapToObj(index -> FloorViolations.missingThemePack(FloorBoundary.floorPath(index)))
                .toList();
    }

    private static List<Violation> notRepeated(Walk walk) {
        List<Optional<String>> packs = walk.indices().mapToObj(walk::chosenPack).toList();
        Map<Optional<String>, Integer> firstFloorOf = Positions.firstIndexOf(packs);
        return walk.indices()
                .filter(index -> packs.get(index).isPresent() && firstFloorOf.get(packs.get(index)) < index)
                .mapToObj(index -> FloorViolations.duplicateThemePack(FloorBoundary.themePackPath(index),
                        packs.get(index).orElseThrow(), FloorBoundary.floorPath(firstFloorOf.get(packs.get(index)))))
                .toList();
    }

    private static List<Violation> sequence(Walk walk) {
        return walk.indices()
                .filter(index -> index > 0
                        && walk.themePack(index) instanceof ThemePack.Chosen
                        && walk.themePack(index - 1) instanceof ThemePack.None)
                .mapToObj(index -> FloorViolations.themePackAfterGap(FloorBoundary.floorPath(index),
                        FloorBoundary.floorPath(index - 1)))
                .toList();
    }

    private static List<Violation> difficultyInRange(Walk walk, int entryFloorCount) {
        return walk.indices()
                .filter(index -> !allows(walk.allowed(index), walk.difficulty(index)))
                .mapToObj(index -> FloorViolations.difficultyNotAllowed(difficultyCode(walk, index, entryFloorCount),
                        FloorBoundary.difficultyPath(index), walk.difficulty(index), walk.allowed(index)))
                .toList();
    }

    private static ErrorCode difficultyCode(Walk walk, int index, int entryFloorCount) {
        return gatesEntry(walk.layout(), index, entryFloorCount)
                ? ErrorCode.INSUFFICIENT_DIFFICULTY_ENTRY
                : SINGLE_DIFFICULTY_CODES.getOrDefault(walk.allowed(index), ErrorCode.OUT_OF_RANGE_DIFFICULTY);
    }

    private static boolean gatesEntry(CategoryFloors layout, int index, int entryFloorCount) {
        return layout.floorCount() > entryFloorCount && index < entryFloorCount;
    }

    private static boolean allows(Set<Integer> allowed, Difficulty difficulty) {
        return difficulty instanceof Difficulty.Set(int value) && allowed.contains(value);
    }

    private static List<Violation> noNormalAfterHard(Walk walk) {
        return walk.indices().filter(walk::isHard).boxed().findFirst().stream()
                .flatMap(hard -> IntStream.range(hard + 1, walk.size())
                        .filter(index -> walk.isNormal(index)
                                && walk.allowed(index).contains(GameConstants.NORMAL_DIFFICULTY))
                        .mapToObj(index -> FloorViolations.normalAfterHard(FloorBoundary.difficultyPath(index),
                                FloorBoundary.difficultyPath(hard))))
                .toList();
    }

    private static List<Violation> giftUnique(Walk walk) {
        return walk.indices()
                .boxed()
                .flatMap(index -> Positions.firstRepeatOf(walk.giftIds(index))
                        .map(giftId -> FloorViolations.duplicateGift(FloorBoundary.giftIdsPath(index), giftId))
                        .stream())
                .toList();
    }
}
