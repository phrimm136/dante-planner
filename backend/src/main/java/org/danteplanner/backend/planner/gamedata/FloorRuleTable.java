package org.danteplanner.backend.planner.gamedata;

import tools.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.shared.failure.Parsed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record FloorRuleTable(
        int schemaVersion,
        Map<MDCategory, CategoryFloors> categories,
        Map<RuleName, Set<Stage>> rules) {

    public record CategoryFloors(int floorCount, List<Set<Integer>> difficulties) {}

    public enum RuleName {
        EVERY_FLOOR_PRESENT("everyFloorPresent"),
        THEME_PACK_PRESENT("themePackPresent"),
        NOT_REPEATED("notRepeated"),
        SEQUENCE("sequence"),
        DIFFICULTY_IN_RANGE("difficultyInRange"),
        NO_NORMAL_AFTER_HARD("noNormalAfterHard"),
        GIFT_UNIQUE("giftUnique");

        private final String tableName;

        RuleName(String tableName) {
            this.tableName = tableName;
        }

        public String tableName() {
            return tableName;
        }
    }

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
        categories = Map.copyOf(categories);
        rules = Map.copyOf(rules);
    }

    public CategoryFloors layout(MDCategory category) {
        CategoryFloors layout = categories.get(category);
        if (layout == null) {
            throw new IllegalStateException("the floor rule table lacks category " + category);
        }
        return layout;
    }

    public int floorCount(MDCategory category) {
        return layout(category).floorCount();
    }

    public int entryFloorCount() {
        return categories.values().stream().mapToInt(CategoryFloors::floorCount).min().orElseThrow();
    }

    public Set<Stage> stagesFor(RuleName rule) {
        Set<Stage> ruleStages = rules.get(rule);
        if (ruleStages == null) {
            throw new IllegalStateException("the floor rule table lacks rule " + rule);
        }
        return ruleStages;
    }

    public static FloorRuleTable parse(JsonNode root) {
        ExactFields fields = ExactFields.of("root", root, KEYS);
        JsonNode schemaVersion = fields.get(SCHEMA_VERSION);
        if (!schemaVersion.isInt() || schemaVersion.asInt() <= 0) {
            throw new IllegalArgumentException("'" + SCHEMA_VERSION + "' must be a positive integer");
        }
        Set<Stage> stages = parseStages(fields.get(STAGES));
        return new FloorRuleTable(
                schemaVersion.asInt(),
                parseCategories(fields.get(CATEGORIES)),
                parseRules(fields.get(RULES), stages));
    }

    private static Set<Stage> parseStages(JsonNode node) {
        Set<Stage> stages = uniqueStrings(STAGES, node).stream().map(Stage::fromTableName)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Stage.class)));
        if (!stages.equals(EnumSet.allOf(Stage.class))) {
            throw new IllegalArgumentException("'" + STAGES + "' must name exactly " + Stage.tableNames());
        }
        return Collections.unmodifiableSet(stages);
    }

    private static Map<MDCategory, CategoryFloors> parseCategories(JsonNode node) {
        Set<String> expected = Arrays.stream(MDCategory.values())
                .map(MDCategory::getValue)
                .collect(Collectors.toSet());
        ExactFields fields = ExactFields.of(CATEGORIES, node, expected);
        Map<MDCategory, CategoryFloors> categories = new EnumMap<>(MDCategory.class);
        for (MDCategory category : MDCategory.values()) {
            categories.put(category, parseCategory(CATEGORIES + "." + category.getValue(),
                    fields.get(category.getValue())));
        }
        return categories;
    }

    private static CategoryFloors parseCategory(String path, JsonNode node) {
        ExactFields fields = ExactFields.of(path, node, CATEGORY_KEYS);
        JsonNode floorCount = fields.get(FLOOR_COUNT);
        if (!floorCount.isInt() || floorCount.asInt() <= 0) {
            throw new IllegalArgumentException("'" + path + "." + FLOOR_COUNT + "' must be a positive integer");
        }
        JsonNode difficulties = fields.get(DIFFICULTIES);
        if (!difficulties.isArray() || difficulties.size() != floorCount.asInt()) {
            throw new IllegalArgumentException("'" + path + "." + DIFFICULTIES + "' must be an array of "
                    + floorCount.asInt() + " difficulty sets, one per floor");
        }
        List<Set<Integer>> floors = new ArrayList<>();
        for (int index = 0; index < difficulties.size(); index++) {
            floors.add(parseDifficultySet(path + "." + DIFFICULTIES + "[" + index + "]", difficulties.path(index)));
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

    private static Map<RuleName, Set<Stage>> parseRules(JsonNode node, Set<Stage> stages) {
        Set<String> ruleNames = Arrays.stream(RuleName.values())
                .map(RuleName::tableName)
                .collect(Collectors.toSet());
        ExactFields fields = ExactFields.of(RULES, node, ruleNames);
        Map<RuleName, Set<Stage>> rules = new EnumMap<>(RuleName.class);
        for (RuleName rule : RuleName.values()) {
            String path = RULES + "." + rule.tableName();
            rules.put(rule, uniqueStrings(path, fields.get(rule.tableName())).stream()
                    .map(name -> declaredStage(path, name, stages))
                    .collect(Collectors.toUnmodifiableSet()));
        }
        return rules;
    }

    private static Stage declaredStage(String path, String name, Set<Stage> stages) {
        return stages.stream()
                .filter(stage -> stage.tableName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + path + "' names unknown stage '" + name
                        + "'; expected one of " + Stage.tableNames()));
    }

    private static Set<String> uniqueStrings(String path, JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + path + "' must be an array of strings");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isString()) {
                throw new IllegalArgumentException("'" + path + "' must hold strings only, got " + value);
            }
            if (!values.add(value.asString())) {
                throw new IllegalArgumentException("'" + path + "' repeats '" + value.asString() + "'");
            }
        }
        return values;
    }

    @Parsed
    private record ExactFields(JsonNode node) {

        private static ExactFields of(String path, JsonNode node, Set<String> keys) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("'" + path + "' must be an object");
            }
            Set<String> present = new HashSet<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (!keys.contains(field.getKey())) {
                    throw new IllegalArgumentException("'" + path + "' has unknown key '" + field.getKey()
                            + "'; expected " + keys);
                }
                present.add(field.getKey());
            }
            if (!present.equals(keys)) {
                throw new IllegalArgumentException("'" + path + "' lacks a key; expected exactly " + keys);
            }
            return new ExactFields(node);
        }

        private JsonNode get(String key) {
            return node.path(key);
        }
    }
}
