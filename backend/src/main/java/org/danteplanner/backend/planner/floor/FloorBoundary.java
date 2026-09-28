package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public final class FloorBoundary {

    public record Parsed(int floorCount, List<ParsedFloor> floors, List<Violation> violations) {

        public Parsed {
            floors = List.copyOf(floors);
            violations = List.copyOf(violations);
        }
    }

    private static final String FLOOR_SELECTIONS = "floorSelections";
    private static final String THEME_PACK_ID = "themePackId";
    private static final String DIFFICULTY = "difficulty";
    private static final String GIFT_IDS = "giftIds";

    private FloorBoundary() {
    }

    public static Parsed parse(JsonNode floorSelections, int floorCount) {
        if (!floorSelections.isArray()) {
            return new Parsed(floorCount, List.of(),
                    List.of(Violation.invalidFieldType(FLOOR_SELECTIONS, "array", floorSelections)));
        }

        List<ParsedFloor> floors = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        for (int index = 0; index < floorSelections.size() && index < floorCount; index++) {
            floors.add(parseFloor(floorSelections.get(index), index, violations));
        }
        violations.sort(Violation.BY_PATH_THEN_CODE);
        return new Parsed(floorCount, floors, violations);
    }

    static String floorPath(int index) {
        return FLOOR_SELECTIONS + "[" + index + "]";
    }

    private static ParsedFloor parseFloor(JsonNode floor, int index, List<Violation> violations) {
        String path = floorPath(index);
        if (!floor.isObject()) {
            violations.add(Violation.invalidFieldType(path, "object", floor));
            return new ParsedFloor.Rejected(index);
        }

        int before = violations.size();
        ThemePack themePack = parseThemePack(floor.path(THEME_PACK_ID), path + "." + THEME_PACK_ID, violations);
        Difficulty difficulty = parseDifficulty(floor.path(DIFFICULTY), path + "." + DIFFICULTY, violations);
        List<String> giftIds = parseGiftIds(floor.path(GIFT_IDS), path + "." + GIFT_IDS, violations);
        if (violations.size() > before) {
            return new ParsedFloor.Rejected(index);
        }
        return new ParsedFloor.Accepted(new FloorSelection(themePack, difficulty, giftIds));
    }

    private static ThemePack parseThemePack(JsonNode node, String path, List<Violation> violations) {
        if (node.isMissingNode() || node.isNull()) {
            return ThemePack.none();
        }
        if (!node.isTextual()) {
            violations.add(Violation.invalidFieldType(path, "string", node));
            return ThemePack.none();
        }
        return node.asText().isEmpty() ? ThemePack.none() : ThemePack.chosen(node.asText());
    }

    private static Difficulty parseDifficulty(JsonNode node, String path, List<Violation> violations) {
        if (node.isMissingNode()) {
            return Difficulty.unset();
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            violations.add(Violation.invalidFieldType(path, "integer", node));
            return Difficulty.unset();
        }
        return Difficulty.of(node.asInt());
    }

    private static List<String> parseGiftIds(JsonNode node, String path, List<Violation> violations) {
        if (node.isMissingNode()) {
            return List.of();
        }
        if (!node.isArray()) {
            violations.add(Violation.invalidFieldType(path, "array", node));
            return List.of();
        }

        List<String> giftIds = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode giftId = node.get(index);
            if (giftId.isTextual()) {
                giftIds.add(giftId.asText());
            } else {
                violations.add(Violation.invalidFieldType(path + "[" + index + "]", "string", giftId));
            }
        }
        return giftIds;
    }
}
