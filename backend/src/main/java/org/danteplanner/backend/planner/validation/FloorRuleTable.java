package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.entity.MDCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record FloorRuleTable(
        int schemaVersion,
        List<String> stages,
        Map<MDCategory, CategoryFloors> categories,
        Map<String, Set<String>> rules) {

    public record CategoryFloors(int floorCount, List<Set<Integer>> difficulties) {}

    public static final Set<String> RULE_NAMES = Set.of(
            "everyFloorPresent", "themePackPresent", "notRepeated", "sequence",
            "difficultyInRange", "noNormalAfterHard", "giftUnique");

    private static final int MIN_DIFFICULTY = 0;
    private static final int MAX_DIFFICULTY = 3;

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String STAGES = "stages";
    private static final String CATEGORIES = "categories";
    private static final String RULES = "rules";
    private static final String FLOOR_COUNT = "floorCount";
    private static final String DIFFICULTIES = "difficulties";
    private static final Set<String> KEYS = Set.of(SCHEMA_VERSION, STAGES, CATEGORIES, RULES);
    private static final Set<String> CATEGORY_KEYS = Set.of(FLOOR_COUNT, DIFFICULTIES);

    public FloorRuleTable {
        stages = List.copyOf(stages);
        categories = Map.copyOf(categories);
        rules = Map.copyOf(rules);
    }

    public int floorCount(MDCategory category) {
        return categories.get(category).floorCount();
    }

    public Set<Integer> allowedDifficulties(MDCategory category, int floorIndex) {
        return categories.get(category).difficulties().get(floorIndex);
    }

    public Set<String> stagesFor(String rule) {
        Set<String> ruleStages = rules.get(rule);
        if (ruleStages == null) {
            throw new IllegalArgumentException("unknown floor rule '" + rule + "'; expected " + RULE_NAMES);
        }
        return ruleStages;
    }

    public static FloorRuleTable parse(JsonNode root) {
        requireObjectWithKeys("root", root, KEYS);
        JsonNode schemaVersion = root.path(SCHEMA_VERSION);
        if (!schemaVersion.isInt() || schemaVersion.asInt() <= 0) {
            throw new IllegalArgumentException("'" + SCHEMA_VERSION + "' must be a positive integer");
        }
        List<String> stages = parseStages(root.path(STAGES));
        return new FloorRuleTable(
                schemaVersion.asInt(),
                stages,
                parseCategories(root.path(CATEGORIES)),
                parseRules(root.path(RULES), Set.copyOf(stages)));
    }

    private static List<String> parseStages(JsonNode node) {
        List<String> stages = new ArrayList<>(uniqueStrings(STAGES, node));
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("'" + STAGES + "' must not be empty");
        }
        return stages;
    }

    private static Map<MDCategory, CategoryFloors> parseCategories(JsonNode node) {
        Set<String> expected = Arrays.stream(MDCategory.values())
                .map(MDCategory::getValue)
                .collect(Collectors.toSet());
        requireObjectWithKeys(CATEGORIES, node, expected);
        Map<MDCategory, CategoryFloors> categories = new EnumMap<>(MDCategory.class);
        for (MDCategory category : MDCategory.values()) {
            JsonNode categoryNode = node.path(category.getValue());
            if (categoryNode.isMissingNode()) {
                throw new IllegalArgumentException("'" + CATEGORIES + "' lacks category '" + category.getValue()
                        + "'; expected exactly " + expected);
            }
            categories.put(category, parseCategory(CATEGORIES + "." + category.getValue(), categoryNode));
        }
        return categories;
    }

    private static CategoryFloors parseCategory(String path, JsonNode node) {
        requireObjectWithKeys(path, node, CATEGORY_KEYS);
        JsonNode floorCount = node.path(FLOOR_COUNT);
        if (!floorCount.isInt() || floorCount.asInt() <= 0) {
            throw new IllegalArgumentException("'" + path + "." + FLOOR_COUNT + "' must be a positive integer");
        }
        JsonNode difficulties = node.path(DIFFICULTIES);
        if (!difficulties.isArray() || difficulties.size() != floorCount.asInt()) {
            throw new IllegalArgumentException("'" + path + "." + DIFFICULTIES + "' must be an array of "
                    + floorCount.asInt() + " difficulty sets, one per floor");
        }
        List<Set<Integer>> floors = new ArrayList<>();
        for (int index = 0; index < difficulties.size(); index++) {
            floors.add(parseDifficultySet(path + "." + DIFFICULTIES + "[" + index + "]", difficulties.get(index)));
        }
        return new CategoryFloors(floorCount.asInt(), List.copyOf(floors));
    }

    private static Set<Integer> parseDifficultySet(String path, JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("'" + path + "' must be a non-empty array of difficulties");
        }
        Set<Integer> difficulties = new LinkedHashSet<>();
        for (JsonNode difficulty : node) {
            if (!difficulty.isInt() || difficulty.asInt() < MIN_DIFFICULTY || difficulty.asInt() > MAX_DIFFICULTY) {
                throw new IllegalArgumentException("'" + path + "' must hold integers in " + MIN_DIFFICULTY + ".."
                        + MAX_DIFFICULTY + ", got " + difficulty);
            }
            if (!difficulties.add(difficulty.asInt())) {
                throw new IllegalArgumentException("'" + path + "' repeats difficulty " + difficulty);
            }
        }
        return Set.copyOf(difficulties);
    }

    private static Map<String, Set<String>> parseRules(JsonNode node, Set<String> stages) {
        requireObjectWithKeys(RULES, node, RULE_NAMES);
        Map<String, Set<String>> rules = new HashMap<>();
        for (String rule : RULE_NAMES) {
            JsonNode ruleNode = node.path(rule);
            if (ruleNode.isMissingNode()) {
                throw new IllegalArgumentException("'" + RULES + "' lacks rule '" + rule + "'; expected exactly "
                        + RULE_NAMES);
            }
            Set<String> ruleStages = uniqueStrings(RULES + "." + rule, ruleNode);
            for (String stage : ruleStages) {
                if (!stages.contains(stage)) {
                    throw new IllegalArgumentException("'" + RULES + "." + rule + "' names unknown stage '" + stage
                            + "'; expected one of " + stages);
                }
            }
            rules.put(rule, Set.copyOf(ruleStages));
        }
        return rules;
    }

    private static Set<String> uniqueStrings(String path, JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + path + "' must be an array of strings");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("'" + path + "' must hold strings only, got " + value);
            }
            if (!values.add(value.asText())) {
                throw new IllegalArgumentException("'" + path + "' repeats '" + value.asText() + "'");
            }
        }
        return values;
    }

    private static void requireObjectWithKeys(String path, JsonNode node, Set<String> keys) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("'" + path + "' must be an object");
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!keys.contains(field.getKey())) {
                throw new IllegalArgumentException("'" + path + "' has unknown key '" + field.getKey()
                        + "'; expected " + keys);
            }
        }
    }
}
