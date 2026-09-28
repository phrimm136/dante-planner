package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.FloorRuleTable;

import java.math.BigInteger;
import java.util.List;

final class FloorArbitraries {

    record RawCase(JsonNode raw, MDCategory category, Stage stage) {}

    static final List<String> THEME_PACK_POOL = List.of("1001", "1002", "1003", "1004", "1005", "1006");
    static final List<String> GIFT_POOL = List.of("9001", "9002", "9003");
    static final int MAX_RAW_FLOORS = 17;
    static final int MAX_GIFTS = 4;
    static final int MAX_APPENDED_FLOORS = 5;
    static final int FILLER_FLOORS = 15;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final List<String> FLOOR_KEYS = List.of("themePackId", "difficulty", "giftIds");
    private static final int ANY_JSON_DEPTH = 3;
    private static final int ANY_JSON_WIDTH = 4;

    private FloorArbitraries() {
    }

    static Arbitrary<JsonNode> rawThemePackId() {
        return Arbitraries.frequencyOf(
                Tuple.of(96, Arbitraries.of(THEME_PACK_POOL).map(NODES::textNode)),
                Tuple.of(2, Arbitraries.just(NODES.textNode(""))),
                Tuple.of(2, Arbitraries.just(NODES.nullNode())),
                Tuple.of(1, Arbitraries.of(THEME_PACK_POOL).map(id -> NODES.numberNode(Integer.parseInt(id)))));
    }

    static Arbitrary<JsonNode> rawDifficulty() {
        return Arbitraries.frequencyOf(
                Tuple.of(98, Arbitraries.integers().between(0, 3).map(NODES::numberNode)),
                Tuple.of(1, Arbitraries.just(NODES.nullNode())),
                Tuple.of(1, Arbitraries.of(NODES.numberNode(1.5), NODES.textNode("1"), NODES.booleanNode(true))));
    }

    static Arbitrary<JsonNode> rawGiftIds() {
        return Arbitraries.frequencyOf(
                Tuple.of(97, Arbitraries.of(GIFT_POOL).list().ofMaxSize(MAX_GIFTS).map(FloorArbitraries::textArray)),
                Tuple.of(1, Arbitraries.just(NODES.nullNode())),
                Tuple.of(1, Arbitraries.just(NODES.textNode("9001"))),
                Tuple.of(1, Arbitraries.of(
                        NODES.arrayNode().add(9001),
                        NODES.arrayNode().add("9001").add(9002))));
    }

    static Arbitrary<JsonNode> rawFloor() {
        Arbitrary<JsonNode> floorObject = Combinators.combine(
                        present(rawThemePackId(), 6), present(rawDifficulty(), 6), present(rawGiftIds(), 3))
                .as(FloorArbitraries::floorOf);
        Arbitrary<JsonNode> nonObjectFloor = Arbitraries.of(
                NODES.nullNode(), NODES.numberNode(7), NODES.textNode("1001"), NODES.arrayNode(),
                NODES.booleanNode(true));
        return Arbitraries.frequencyOf(Tuple.of(99, floorObject), Tuple.of(1, nonObjectFloor));
    }

    static Arbitrary<List<JsonNode>> rawFloorList(int minSize, int maxSize) {
        return rawFloor().list().ofMinSize(minSize).ofMaxSize(maxSize);
    }

    static Arbitrary<JsonNode> rawFloorSelections() {
        return rawFloorList(0, MAX_RAW_FLOORS).map(FloorArbitraries::array);
    }

    static Arbitrary<JsonNode> rawFloorSelectionsOrGarbage() {
        Arbitrary<JsonNode> nonArray = Arbitraries.of(
                NODES.nullNode(), NODES.objectNode(), NODES.textNode("floors"), NODES.numberNode(5));
        return Arbitraries.frequencyOf(Tuple.of(20, rawFloorSelections()), Tuple.of(1, nonArray));
    }

    static Arbitrary<MDCategory> category(FloorRuleTable table) {
        return Arbitraries.of(table.categories().keySet());
    }

    static Arbitrary<Stage> stage() {
        return Arbitraries.of(Stage.class);
    }

    static Arbitrary<RawCase> rawCase(FloorRuleTable table) {
        return Combinators.combine(rawFloorSelectionsOrGarbage(), category(table), stage()).as(RawCase::new);
    }

    static Arbitrary<JsonNode> pastCountFloor() {
        return Arbitraries.oneOf(rawFloor(), anyJson());
    }

    static Arbitrary<JsonNode> anyRaw() {
        Arbitrary<JsonNode> anyFloor = Arbitraries.oneOf(anyJson(), Combinators.combine(
                        present(anyJson(), 1), present(anyJson(), 1), present(anyJson(), 1))
                .as(FloorArbitraries::floorOf));
        return Arbitraries.oneOf(
                rawFloorSelectionsOrGarbage(),
                anyJson(),
                Arbitraries.just(MissingNode.getInstance()),
                anyFloor.list().ofMaxSize(MAX_RAW_FLOORS).map(FloorArbitraries::array));
    }

    static Arbitrary<JsonNode> anyJson() {
        Arbitrary<JsonNode> leaf = Arbitraries.oneOf(
                Arbitraries.just(NODES.nullNode()),
                Arbitraries.of(true, false).map(NODES::booleanNode),
                Arbitraries.integers().map(NODES::numberNode),
                Arbitraries.longs().map(NODES::numberNode),
                Arbitraries.bigIntegers().between(BigInteger.valueOf(Long.MIN_VALUE).pow(2).negate(),
                        BigInteger.valueOf(Long.MAX_VALUE).pow(2)).map(NODES::numberNode),
                Arbitraries.doubles().map(NODES::numberNode),
                Arbitraries.bigDecimals().map(value -> (JsonNode) NODES.numberNode(value)),
                Arbitraries.strings().ofMaxLength(8).map(NODES::textNode),
                Arbitraries.of(FLOOR_KEYS).map(NODES::textNode));
        return Arbitraries.recursive(() -> leaf, FloorArbitraries::container, 0, ANY_JSON_DEPTH);
    }

    private static Arbitrary<JsonNode> container(Arbitrary<JsonNode> element) {
        Arbitrary<String> key = Arbitraries.oneOf(Arbitraries.of(FLOOR_KEYS), Arbitraries.strings().ofMaxLength(6));
        Arbitrary<JsonNode> object = Arbitraries.maps(key, element).ofMaxSize(ANY_JSON_WIDTH).map(fields -> {
            ObjectNode node = NODES.objectNode();
            fields.forEach(node::set);
            return node;
        });
        return Arbitraries.oneOf(element, element.list().ofMaxSize(ANY_JSON_WIDTH).map(FloorArbitraries::array),
                object);
    }

    private static JsonNode floorOf(JsonNode themePackId, JsonNode difficulty, JsonNode giftIds) {
        ObjectNode floor = NODES.objectNode();
        List<JsonNode> values = List.of(themePackId, difficulty, giftIds);
        for (int index = 0; index < FLOOR_KEYS.size(); index++) {
            if (!values.get(index).isMissingNode()) {
                floor.set(FLOOR_KEYS.get(index), values.get(index));
            }
        }
        return floor;
    }

    private static Arbitrary<JsonNode> present(Arbitrary<JsonNode> value, int presentToAbsent) {
        return Arbitraries.frequencyOf(
                Tuple.of(presentToAbsent, value),
                Tuple.of(1, Arbitraries.just(MissingNode.getInstance())));
    }

    static ArrayNode array(List<JsonNode> elements) {
        ArrayNode array = NODES.arrayNode();
        elements.forEach(array::add);
        return array;
    }

    private static JsonNode textArray(List<String> values) {
        ArrayNode array = NODES.arrayNode();
        values.forEach(array::add);
        return array;
    }
}
