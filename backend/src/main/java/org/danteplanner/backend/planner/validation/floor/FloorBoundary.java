package org.danteplanner.backend.planner.validation.floor;

import tools.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable.CategoryFloors;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentSchema;
import org.danteplanner.backend.planner.validation.Violation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

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

    private static final String FLOOR_SELECTIONS = "floorSelections";
    private static final ContentPath FLOOR_SELECTIONS_PATH = ContentPath.of(FLOOR_SELECTIONS);
    private static final String THEME_PACK_ID = "themePackId";
    private static final String DIFFICULTY = "difficulty";
    private static final String GIFT_IDS = "giftIds";
    private static final FloorSelection EMPTY_SALVAGE =
            new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of());

    private FloorBoundary() {
    }

    public static Parsed parse(JsonNode content, CategoryFloors layout) {
        List<Violation> violations = ContentSchema.planner().violations(content).stream()
                .filter(violation -> violation.path().within(FLOOR_SELECTIONS_PATH))
                .toList();
        JsonNode floorSelections = content.path(FLOOR_SELECTIONS);
        return switch (floorSelections.getNodeType()) {
            case ARRAY -> new Parsed(layout,
                    IntStream.range(0, Math.min(floorSelections.size(), layout.floorCount()))
                            .<ParsedFloor>mapToObj(index -> violations.stream()
                                    .anyMatch(violation -> violation.path().within(floorPath(index)))
                                    ? new ParsedFloor.Rejected(read(floorSelections.get(index)))
                                    : new ParsedFloor.Accepted(read(floorSelections.get(index))))
                            .toList(),
                    violations);
            default -> new Parsed(layout, List.of(), violations);
        };
    }

    static FloorSelection read(JsonNode floor) {
        return switch (floor.getNodeType()) {
            case OBJECT -> new FloorSelection(themePack(floor.path(THEME_PACK_ID)), difficulty(floor.path(DIFFICULTY)),
                    giftIds(floor.path(GIFT_IDS)));
            default -> EMPTY_SALVAGE;
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

    private static ThemePack themePack(JsonNode node) {
        return switch (node.getNodeType()) {
            case STRING, NUMBER -> node.asString().isEmpty() ? ThemePack.none() : ThemePack.chosen(node.asString());
            default -> ThemePack.none();
        };
    }

    private static Difficulty difficulty(JsonNode node) {
        return switch (node.getNodeType()) {
            case NUMBER -> integralValue(node).map(FloorBoundary::difficultyOf).orElseGet(Difficulty::unset);
            default -> Difficulty.unset();
        };
    }

    private static Difficulty difficultyOf(BigInteger value) {
        return value.bitLength() < Integer.SIZE ? Difficulty.of(value.intValue()) : Difficulty.outOfRange(value);
    }

    private static Optional<BigInteger> integralValue(JsonNode number) {
        return switch (number.numberType()) {
            case INT, LONG, BIG_INTEGER -> Optional.of(number.bigIntegerValue());
            case FLOAT, DOUBLE, BIG_DECIMAL -> Optional.of(number)
                    .filter(fraction -> Double.isFinite(fraction.doubleValue()))
                    .map(JsonNode::decimalValue)
                    .filter(decimal -> decimal.stripTrailingZeros().scale() <= 0)
                    .map(BigDecimal::toBigInteger);
        };
    }

    private static List<String> giftIds(JsonNode node) {
        return switch (node.getNodeType()) {
            case ARRAY -> StreamSupport.stream(node.spliterator(), false)
                    .flatMap(giftId -> giftId(giftId).stream())
                    .toList();
            default -> List.of();
        };
    }

    private static Optional<String> giftId(JsonNode giftId) {
        return switch (giftId.getNodeType()) {
            case STRING, NUMBER -> Optional.of(giftId.asString());
            default -> Optional.empty();
        };
    }
}
