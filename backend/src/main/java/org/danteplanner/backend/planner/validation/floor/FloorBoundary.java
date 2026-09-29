package org.danteplanner.backend.planner.validation.floor;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable.CategoryFloors;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.JsonNumbers;
import org.danteplanner.backend.planner.validation.Violation;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class FloorBoundary {

    public record Parsed(CategoryFloors layout, List<ParsedFloor> floors, List<Violation> violations) {

        public Parsed {
            floors = List.copyOf(floors);
            violations = List.copyOf(violations);
            if (floors.size() > layout.floorCount()) {
                throw new IllegalArgumentException(String.format("%d parsed floors exceed the layout's %d floors",
                        floors.size(), layout.floorCount()));
            }
        }

        public List<FloorSelection> accepted() {
            return floors.stream().flatMap(floor -> floor.accepted().stream()).toList();
        }

        List<FloorSelection> indexed() {
            return floors.stream().map(ParsedFloor::indexed).toList();
        }
    }

    private record FloorResult(ParsedFloor floor, List<Violation> violations) {
    }

    private static final String FLOOR_SELECTIONS = "floorSelections";
    private static final ContentPath FLOOR_SELECTIONS_PATH = ContentPath.of(FLOOR_SELECTIONS);
    private static final String THEME_PACK_ID = "themePackId";
    private static final String DIFFICULTY = "difficulty";
    private static final String GIFT_IDS = "giftIds";
    private static final FloorSelection EMPTY_SALVAGE =
            new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of());

    private FloorBoundary() {
    }

    public static Parsed parse(JsonNode floorSelections, CategoryFloors layout) {
        return switch (floorSelections.getNodeType()) {
            case ARRAY -> {
                List<FloorResult> results = IntStream.range(0, Math.min(floorSelections.size(), layout.floorCount()))
                        .mapToObj(index -> parseFloor(floorSelections.get(index), index))
                        .toList();
                yield new Parsed(layout,
                        results.stream().map(FloorResult::floor).toList(),
                        results.stream()
                                .flatMap(result -> result.violations().stream())
                                .sorted(Violation.BY_PATH_THEN_CODE)
                                .toList());
            }
            default -> new Parsed(layout, List.of(),
                    List.of(Violation.invalidFieldType(FLOOR_SELECTIONS_PATH, JsonKind.ARRAY, floorSelections)));
        };
    }

    static ContentPath floorPath(int index) {
        return FLOOR_SELECTIONS_PATH.index(index);
    }

    static ContentPath themePackPath(int index) {
        return floorPath(index).field(THEME_PACK_ID);
    }

    static ContentPath difficultyPath(int index) {
        return floorPath(index).field(DIFFICULTY);
    }

    static ContentPath giftIdsPath(int index) {
        return floorPath(index).field(GIFT_IDS);
    }

    private static FloorResult parseFloor(JsonNode floor, int index) {
        return switch (floor.getNodeType()) {
            case OBJECT -> parseObjectFloor(floor, index);
            default -> new FloorResult(new ParsedFloor.Rejected(EMPTY_SALVAGE),
                    List.of(Violation.invalidFieldType(floorPath(index), JsonKind.OBJECT, floor)));
        };
    }

    private static FloorResult parseObjectFloor(JsonNode floor, int index) {
        FieldResult<ThemePack> themePack = parseThemePack(floor.path(THEME_PACK_ID), themePackPath(index));
        FieldResult<Difficulty> difficulty = parseDifficulty(floor.path(DIFFICULTY), difficultyPath(index));
        FieldResult<List<String>> giftIds = parseGiftIds(floor.path(GIFT_IDS), giftIdsPath(index));
        List<Violation> violations = Stream.of(themePack.violations(), difficulty.violations(), giftIds.violations())
                .flatMap(List::stream)
                .toList();
        FloorSelection selection = new FloorSelection(themePack.value(), difficulty.value(), giftIds.value());
        return new FloorResult(violations.isEmpty()
                ? new ParsedFloor.Accepted(selection)
                : new ParsedFloor.Rejected(selection), violations);
    }

    private static FieldResult<ThemePack> parseThemePack(JsonNode node, ContentPath path) {
        return switch (node.getNodeType()) {
            case MISSING, NULL -> FieldResult.of(ThemePack.none());
            case STRING -> FieldResult.of(node.asText().isEmpty() ? ThemePack.none() : ThemePack.chosen(node.asText()));
            case NUMBER -> FieldResult.rejected(ThemePack.chosen(node.asText()),
                    Violation.invalidFieldType(path, JsonKind.STRING, node));
            default -> FieldResult.rejected(ThemePack.none(), Violation.invalidFieldType(path, JsonKind.STRING, node));
        };
    }

    private static FieldResult<Difficulty> parseDifficulty(JsonNode node, ContentPath path) {
        return switch (node.getNodeType()) {
            case MISSING -> FieldResult.of(Difficulty.unset());
            case NUMBER -> JsonNumbers.integralValue(node)
                    .map(value -> FieldResult.of(difficultyOf(value)))
                    .orElseGet(() -> FieldResult.rejected(Difficulty.unset(),
                            Violation.invalidFieldType(path, JsonKind.INTEGER, node)));
            default -> FieldResult.rejected(Difficulty.unset(), Violation.invalidFieldType(path, JsonKind.INTEGER, node));
        };
    }

    private static Difficulty difficultyOf(BigInteger value) {
        return value.bitLength() < Integer.SIZE ? Difficulty.of(value.intValue()) : Difficulty.outOfRange(value);
    }

    private static FieldResult<List<String>> parseGiftIds(JsonNode node, ContentPath path) {
        return switch (node.getNodeType()) {
            case MISSING -> FieldResult.of(List.of());
            case ARRAY -> parseGiftIdArray(node, path);
            default -> FieldResult.rejected(List.of(), Violation.invalidFieldType(path, JsonKind.ARRAY, node));
        };
    }

    private static FieldResult<List<String>> parseGiftIdArray(JsonNode giftIds, ContentPath path) {
        List<FieldResult<Optional<String>>> gifts = IntStream.range(0, giftIds.size())
                .mapToObj(index -> parseGiftId(giftIds.get(index), path.index(index)))
                .toList();
        return new FieldResult<>(
                gifts.stream().flatMap(gift -> gift.value().stream()).toList(),
                gifts.stream().flatMap(gift -> gift.violations().stream()).toList());
    }

    private static FieldResult<Optional<String>> parseGiftId(JsonNode giftId, ContentPath path) {
        return switch (giftId.getNodeType()) {
            case STRING -> FieldResult.of(Optional.of(giftId.asText()));
            case NUMBER -> FieldResult.rejected(Optional.of(giftId.asText()),
                    Violation.invalidFieldType(path, JsonKind.STRING, giftId));
            default -> FieldResult.rejected(Optional.empty(), Violation.invalidFieldType(path, JsonKind.STRING, giftId));
        };
    }
}
