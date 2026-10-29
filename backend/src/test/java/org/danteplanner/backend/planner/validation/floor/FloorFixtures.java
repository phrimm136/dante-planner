package org.danteplanner.backend.planner.validation.floor;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class FloorFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final FloorRuleTable TABLE =
            new GameDataLoader(MAPPER).loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));
    static final Path CORPUS_FILE = Path.of("../testdata/planner-floor-rules.corpus.json");

    private static final JsonNode BASE = readCorpus().path("base");

    private FloorFixtures() {
    }

    static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    static ObjectNode floor(String themePackId, int difficulty, String... giftIds) {
        ObjectNode floor = MAPPER.createObjectNode()
                .put("themePackId", themePackId)
                .put("difficulty", difficulty);
        ArrayNode gifts = floor.putArray("giftIds");
        List.of(giftIds).forEach(gifts::add);
        return floor;
    }

    static ArrayNode floors(JsonNode... floors) {
        return MAPPER.createArrayNode().addAll(List.of(floors));
    }

    static ObjectNode content(JsonNode floorSelections) {
        ObjectNode content = (ObjectNode) BASE.deepCopy();
        if (floorSelections.isMissingNode()) {
            content.remove("floorSelections");
        } else {
            content.set("floorSelections", floorSelections);
        }
        return content;
    }

    static FloorBoundary.Parsed parse(JsonNode floorSelections, MDCategory category) {
        return FloorBoundary.parse(content(floorSelections), TABLE.layout(category));
    }

    static FloorSelection selection(String themePackId, int difficulty, String... giftIds) {
        return new FloorSelection(ThemePack.chosen(themePackId), Difficulty.of(difficulty), List.of(giftIds));
    }

    static FloorBoundary.Parsed accepted(MDCategory category, FloorSelection... floors) {
        return new FloorBoundary.Parsed(TABLE.layout(category),
                Stream.of(floors).<ParsedFloor>map(ParsedFloor.Accepted::new).toList(), List.of());
    }

    static JsonNode readCorpus() {
        return MAPPER.readTree(CORPUS_FILE.toFile());
    }

    static ArrayNode outcomeFloors(List<FloorSelection> floors) {
        ArrayNode array = MAPPER.createArrayNode();
        floors.forEach(floor -> array.add(outcomeFloor(floor)));
        return array;
    }

    private static ObjectNode outcomeFloor(FloorSelection floor) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (floor.themePack()) {
            case ThemePack.Chosen(String id) -> node.put("themePackId", id);
            case ThemePack.None() -> node.putNull("themePackId");
        }
        switch (floor.difficulty()) {
            case Difficulty.Set(int value) -> node.put("difficulty", value);
            case Difficulty.OutOfRange(BigInteger value) -> node.put("difficulty", value);
            case Difficulty.Unset() -> node.putNull("difficulty");
        }
        ArrayNode giftIds = node.putArray("giftIds");
        floor.giftIds().forEach(giftIds::add);
        return node;
    }
}
