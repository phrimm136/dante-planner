package org.danteplanner.backend.planner.validation.floor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.IntDistribution;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FloorGenerators {

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
    private static final int MAX_BIG_INTEGER_DIGITS = 37;
    private static final int MAX_BIG_DECIMAL_SCALE = 10;
    private static final char MIN_STRING_CHAR = ' ';
    private static final char MAX_STRING_CHAR = '⿿';

    private FloorGenerators() {
    }

    static Generator<JsonNode> rawThemePackId() {
        LinkedHashMap<Generator<? extends JsonNode>, Integer> weighted = new LinkedHashMap<>();
        weighted.put(Generator.sampledFrom(THEME_PACK_POOL).map(NODES::textNode), 96);
        weighted.put(Generator.constant(NODES.textNode("")), 2);
        weighted.put(Generator.constant(NODES.nullNode()), 2);
        weighted.put(Generator.sampledFrom(THEME_PACK_POOL).map(id -> NODES.numberNode(Integer.parseInt(id))), 1);
        return Generator.frequency(weighted);
    }

    static Generator<JsonNode> rawDifficulty() {
        return Generator.frequency(
                98, Generator.integers(0, 3).map(NODES::numberNode),
                1, Generator.constant(NODES.nullNode()),
                1, Generator.<JsonNode>sampledFrom(NODES.numberNode(1.5), NODES.textNode("1"), NODES.booleanNode(true)));
    }

    static Generator<JsonNode> rawGiftIds() {
        LinkedHashMap<Generator<? extends JsonNode>, Integer> weighted = new LinkedHashMap<>();
        weighted.put(Generator.listsOf(IntDistribution.uniform(0, MAX_GIFTS), Generator.sampledFrom(GIFT_POOL))
                .map(FloorGenerators::textArray), 97);
        weighted.put(Generator.constant(NODES.nullNode()), 1);
        weighted.put(Generator.constant(NODES.textNode("9001")), 1);
        weighted.put(Generator.<JsonNode>sampledFrom(
                NODES.arrayNode().add(9001),
                NODES.arrayNode().add("9001").add(9002)), 1);
        return Generator.frequency(weighted);
    }

    static Generator<JsonNode> rawFloor() {
        Generator<JsonNode> floorObject = floorOf(
                present(rawThemePackId(), 6), present(rawDifficulty(), 6), present(rawGiftIds(), 3));
        Generator<JsonNode> nonObjectFloor = Generator.sampledFrom(
                NODES.nullNode(), NODES.numberNode(7), NODES.textNode("1001"), NODES.arrayNode(),
                NODES.booleanNode(true));
        return Generator.frequency(99, floorObject, 1, nonObjectFloor);
    }

    static Generator<List<JsonNode>> rawFloorList(int minSize, int maxSize) {
        return Generator.listsOf(IntDistribution.uniform(minSize, maxSize), rawFloor());
    }

    static Generator<JsonNode> rawFloorSelections() {
        return rawFloorList(0, MAX_RAW_FLOORS).map(FloorGenerators::array);
    }

    static Generator<JsonNode> rawFloorSelectionsOrGarbage() {
        Generator<JsonNode> nonArray = Generator.sampledFrom(
                NODES.nullNode(), NODES.objectNode(), NODES.textNode("floors"), NODES.numberNode(5));
        return Generator.frequency(20, rawFloorSelections(), 1, nonArray);
    }

    static Generator<MDCategory> category(FloorRuleTable table) {
        return Generator.sampledFrom(table.categories().keySet().stream().sorted().toList());
    }

    static Generator<Stage> stage() {
        return Generator.sampledFrom(Stage.values());
    }

    static Generator<RawCase> rawCase(FloorRuleTable table) {
        return rawCase(table, rawFloorSelectionsOrGarbage());
    }

    static Generator<RawCase> rawCase(FloorRuleTable table, Generator<JsonNode> raw) {
        Generator<MDCategory> category = category(table);
        Generator<Stage> stage = stage();
        return Generator.from(env -> new RawCase(env.generate(raw), env.generate(category), env.generate(stage)));
    }

    static Generator<JsonNode> wellShapedFloor() {
        return floorOf(
                Generator.frequency(
                        9, Generator.sampledFrom(THEME_PACK_POOL).map(id -> (JsonNode) NODES.textNode(id)),
                        1, Generator.constant(NODES.nullNode())),
                Generator.integers(0, 3).map(difficulty -> (JsonNode) NODES.numberNode(difficulty)),
                Generator.listsOf(IntDistribution.uniform(0, MAX_GIFTS), Generator.sampledFrom(GIFT_POOL))
                        .map(FloorGenerators::textArray));
    }

    static Generator<JsonNode> anyRaw() {
        Generator<JsonNode> anyFloor = Generator.anyOf(anyJson(),
                floorOf(present(anyJson(), 1), present(anyJson(), 1), present(anyJson(), 1)));
        return Generator.anyOf(
                rawFloorSelectionsOrGarbage(),
                anyJson(),
                Generator.constant(MissingNode.getInstance()),
                Generator.listsOf(IntDistribution.uniform(0, MAX_RAW_FLOORS), anyFloor).map(FloorGenerators::array));
    }

    static Generator<JsonNode> anyJson() {
        return Generator.integers(0, ANY_JSON_DEPTH).flatMap(FloorGenerators::nested);
    }

    private static Generator<JsonNode> nested(int depth) {
        if (depth == 0) {
            return leaf();
        }
        return container(nested(depth - 1));
    }

    private static Generator<JsonNode> leaf() {
        return Generator.anyOf(
                Generator.constant(NODES.nullNode()),
                Generator.booleans().map(NODES::booleanNode),
                Generator.integers().map(NODES::numberNode),
                Generator.zipWith(Generator.integers(), Generator.integers(),
                        (hi, lo) -> ((long) hi << 32) | (lo & 0xFFFFFFFFL)).map(NODES::numberNode),
                bigIntegers().map(NODES::numberNode),
                Generator.doubles().suchThat(Double::isFinite).map(NODES::numberNode),
                Generator.zipWith(bigIntegers(), Generator.integers(0, MAX_BIG_DECIMAL_SCALE),
                        (unscaled, scale) -> new BigDecimal(unscaled, scale)).map(value -> (JsonNode) NODES.numberNode(value)),
                strings(8).map(NODES::textNode),
                Generator.sampledFrom(FLOOR_KEYS).map(NODES::textNode));
    }

    private static Generator<BigInteger> bigIntegers() {
        return Generator.zipWith(Generator.booleans(),
                Generator.stringsOf(IntDistribution.uniform(1, MAX_BIG_INTEGER_DIGITS), Generator.digits()),
                (negative, digits) -> new BigInteger(negative ? "-" + digits : digits));
    }

    private static Generator<String> strings(int maxLength) {
        return Generator.stringsOf(IntDistribution.uniform(0, maxLength),
                Generator.charsInRange(MIN_STRING_CHAR, MAX_STRING_CHAR));
    }

    private static Generator<JsonNode> container(Generator<JsonNode> element) {
        Generator<String> key = Generator.anyOf(Generator.sampledFrom(FLOOR_KEYS), strings(6));
        Generator<JsonNode> object = Generator.listsOf(IntDistribution.uniform(0, ANY_JSON_WIDTH),
                Generator.zipWith(key, element, Map::entry))
                .suchThat(fields -> fields.stream().map(Map.Entry::getKey).distinct().count() == fields.size())
                .map(fields -> {
                    ObjectNode node = NODES.objectNode();
                    fields.forEach(field -> node.set(field.getKey(), field.getValue()));
                    return node;
                });
        return Generator.anyOf(element,
                Generator.listsOf(IntDistribution.uniform(0, ANY_JSON_WIDTH), element).map(FloorGenerators::array),
                object);
    }

    private static Generator<JsonNode> floorOf(
            Generator<JsonNode> themePackId, Generator<JsonNode> difficulty, Generator<JsonNode> giftIds) {
        return Generator.from(env -> floorOf(env.generate(themePackId), env.generate(difficulty), env.generate(giftIds)));
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

    private static Generator<JsonNode> present(Generator<JsonNode> value, int presentToAbsent) {
        return Generator.frequency(presentToAbsent, value, 1, Generator.constant(MissingNode.getInstance()));
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
