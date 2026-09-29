package org.danteplanner.backend.planner.validation.floor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.GameDataLoader;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;

final class FloorFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final FloorRuleTable TABLE =
            new GameDataLoader(MAPPER).loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));

    private FloorFixtures() {
    }

    static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
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
