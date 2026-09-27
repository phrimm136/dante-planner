package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;

public record PlannerIdMigrations(Map<Kind, Map<String, String>> renames, Map<Kind, Set<String>> drops) {

    public enum Kind {
        IDENTITY("identity"),
        EGO("ego"),
        EGO_GIFT("egoGift"),
        THEME_PACK("themePack"),
        START_BUFF("startBuff");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static Kind fromKey(String key) {
            return Arrays.stream(values())
                    .filter(kind -> kind.key.equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown entity type '" + key + "'; expected one of "
                                    + Arrays.stream(values()).map(Kind::key).toList()));
        }
    }

    private static final String RENAME = "rename";
    private static final String DROP = "drop";
    private static final Set<String> SECTION_KEYS = Set.of(RENAME, DROP);
    private static final String REQUIRED_EGO_TYPE = "ZAYIN";

    public static final PlannerIdMigrations EMPTY = new PlannerIdMigrations(Map.of(), Map.of());

    public PlannerIdMigrations {
        renames = copyRenames(renames);
        drops = copyDrops(drops);
    }

    public static PlannerIdMigrations parse(JsonNode root) {
        if (!root.isObject()) {
            throw new IllegalArgumentException("root must be an object keyed by entity type");
        }

        Map<Kind, Map<String, String>> renames = new EnumMap<>(Kind.class);
        Map<Kind, Set<String>> drops = new EnumMap<>(Kind.class);

        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            Kind kind = Kind.fromKey(entry.getKey());
            JsonNode section = entry.getValue();
            if (!section.isObject()) {
                throw new IllegalArgumentException("'" + kind.key() + "' must be an object");
            }
            for (String field : iterable(section)) {
                if (!SECTION_KEYS.contains(field)) {
                    throw new IllegalArgumentException(
                            "'" + kind.key() + "' has unknown field '" + field + "'; expected rename or drop");
                }
            }
            renames.put(kind, parseRenames(kind, section.path(RENAME)));
            drops.put(kind, parseDrops(kind, section.path(DROP)));
        }

        return new PlannerIdMigrations(renames, drops);
    }

    private static Map<String, String> parseRenames(Kind kind, JsonNode node) {
        Map<String, String> renames = new HashMap<>();
        if (node.isMissingNode()) {
            return renames;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("'" + kind.key() + ".rename' must be an object");
        }
        for (Map.Entry<String, JsonNode> rename : node.properties()) {
            if (!rename.getValue().isTextual() || rename.getValue().asText().isEmpty()) {
                throw new IllegalArgumentException("'" + kind.key() + ".rename." + rename.getKey()
                        + "' must be a non-empty string id");
            }
            renames.put(rename.getKey(), rename.getValue().asText());
        }
        return renames;
    }

    private static Set<String> parseDrops(Kind kind, JsonNode node) {
        Set<String> drops = new HashSet<>();
        if (node.isMissingNode()) {
            return drops;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + kind.key() + ".drop' must be an array");
        }
        for (JsonNode id : node) {
            if (!id.isTextual() || id.asText().isEmpty()) {
                throw new IllegalArgumentException("'" + kind.key() + ".drop' must hold non-empty string ids");
            }
            drops.add(id.asText());
        }
        return drops;
    }

    public List<String> inconsistenciesWith(Map<Kind, Predicate<String>> known) {
        List<String> problems = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            Predicate<String> exists = known.get(kind);
            Map<String, String> kindRenames = renames.getOrDefault(kind, Map.of());
            Set<String> kindDrops = drops.getOrDefault(kind, Set.of());

            kindRenames.forEach((from, to) -> {
                if (!exists.test(to)) {
                    problems.add(kind.key() + " rename " + from + " -> " + to + ": target is not in the game data");
                }
                if (kindRenames.containsKey(to)) {
                    problems.add(kind.key() + " rename " + from + " -> " + to + ": target is itself renamed");
                }
                if (kindDrops.contains(to)) {
                    problems.add(kind.key() + " rename " + from + " -> " + to + ": target is dropped");
                }
                if (kindDrops.contains(from)) {
                    problems.add(kind.key() + " id " + from + " is both renamed and dropped");
                }
            });
            kindDrops.forEach(id -> {
                if (kind == Kind.IDENTITY) {
                    problems.add("identity drop " + id + ": an equipped identity has no empty form; supply a rename");
                }
                if (kind == Kind.THEME_PACK) {
                    problems.add("themePack drop " + id + ": a floor's theme pack has no empty form; supply a rename");
                }
                if (exists.test(id)) {
                    problems.add(kind.key() + " drop " + id + ": id is still in the game data");
                }
            });
        }
        return problems;
    }

    public JsonNode normalize(JsonNode content) {
        if (!content.isObject()) {
            return content;
        }
        ObjectNode root = ((ObjectNode) content).deepCopy();

        normalizeEquipment(root.path("equipment"));
        normalizeGiftArray(root.path("selectedGiftIds"));
        normalizeGiftArray(root.path("observationGiftIds"));
        normalizeGiftArray(root.path("comprehensiveGiftIds"));
        normalizeBuffArray(root.path("selectedBuffIds"));
        normalizeFloors(root.path("floorSelections"));

        return root;
    }

    private void normalizeEquipment(JsonNode equipment) {
        if (!equipment.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> sinner : equipment.properties()) {
            JsonNode sinnerEquipment = sinner.getValue();
            if (!sinnerEquipment.isObject()) {
                continue;
            }
            renameIdField(sinnerEquipment.path("identity"), Kind.IDENTITY);

            JsonNode egos = sinnerEquipment.path("egos");
            if (!egos.isObject()) {
                continue;
            }
            List<String> droppedSlots = new ArrayList<>();
            for (Map.Entry<String, JsonNode> slot : egos.properties()) {
                renameIdField(slot.getValue(), Kind.EGO);
                JsonNode idNode = slot.getValue().path("id");
                if (!REQUIRED_EGO_TYPE.equals(slot.getKey()) && idNode.isTextual()
                        && isDropped(Kind.EGO, idNode.asText())) {
                    droppedSlots.add(slot.getKey());
                }
            }
            ((ObjectNode) egos).remove(droppedSlots);
        }
    }

    private void renameIdField(JsonNode owner, Kind kind) {
        if (!owner.isObject()) {
            return;
        }
        JsonNode idNode = owner.path("id");
        if (idNode.isTextual()) {
            ((ObjectNode) owner).put("id", renamed(kind, idNode.asText()));
        }
    }

    private void normalizeGiftArray(JsonNode array) {
        rewriteArray(array, element -> {
            if (!element.isTextual()) {
                return Optional.of(element);
            }
            String giftId = element.asText();
            Matcher matcher = GameDataRegistry.GIFT_ENHANCEMENT_PATTERN.matcher(giftId);
            String base = matcher.matches() ? matcher.group(1) : giftId;
            String enhancement = giftId.substring(0, giftId.length() - base.length());
            if (isDropped(Kind.EGO_GIFT, base)) {
                return Optional.empty();
            }
            return Optional.of(TextNode.valueOf(enhancement + renamed(Kind.EGO_GIFT, base)));
        });
    }

    private void normalizeBuffArray(JsonNode array) {
        rewriteArray(array, element -> {
            if (!element.isIntegralNumber() || !element.canConvertToInt()) {
                return Optional.of(element);
            }
            String buffId = String.valueOf(element.asInt());
            if (isDropped(Kind.START_BUFF, buffId)) {
                return Optional.empty();
            }
            String target = renamed(Kind.START_BUFF, buffId);
            return Optional.of(target.equals(buffId) ? element : IntNode.valueOf(Integer.parseInt(target)));
        });
    }

    private static void rewriteArray(JsonNode array, Function<JsonNode, Optional<JsonNode>> migrate) {
        if (!array.isArray()) {
            return;
        }
        ArrayNode elements = (ArrayNode) array;
        List<JsonNode> migrated = new ArrayList<>();
        Set<JsonNode> renamedValues = new HashSet<>();
        for (JsonNode element : elements) {
            migrate.apply(element).ifPresent(result -> {
                if (!result.equals(element)) {
                    renamedValues.add(result);
                }
                migrated.add(result);
            });
        }

        Set<JsonNode> kept = new HashSet<>();
        elements.removeAll();
        for (JsonNode element : migrated) {
            if (renamedValues.contains(element) && !kept.add(element)) {
                continue;
            }
            elements.add(element);
        }
    }

    private void normalizeFloors(JsonNode floors) {
        if (!floors.isArray()) {
            return;
        }
        for (JsonNode floor : floors) {
            if (!floor.isObject()) {
                continue;
            }
            JsonNode themePackNode = floor.path("themePackId");
            if (themePackNode.isTextual()) {
                ((ObjectNode) floor).put("themePackId", renamed(Kind.THEME_PACK, themePackNode.asText()));
            }
            normalizeGiftArray(floor.path("giftIds"));
        }
    }

    private String renamed(Kind kind, String id) {
        return renames.getOrDefault(kind, Map.of()).getOrDefault(id, id);
    }

    private boolean isDropped(Kind kind, String id) {
        return drops.getOrDefault(kind, Set.of()).contains(id);
    }

    private static Iterable<String> iterable(JsonNode node) {
        return node::fieldNames;
    }

    private static Map<Kind, Map<String, String>> copyRenames(Map<Kind, Map<String, String>> source) {
        Map<Kind, Map<String, String>> copy = new EnumMap<>(Kind.class);
        source.forEach((kind, map) -> copy.put(kind, Map.copyOf(map)));
        return Map.copyOf(copy);
    }

    private static Map<Kind, Set<String>> copyDrops(Map<Kind, Set<String>> source) {
        Map<Kind, Set<String>> copy = new EnumMap<>(Kind.class);
        source.forEach((kind, set) -> copy.put(kind, Set.copyOf(set)));
        return Map.copyOf(copy);
    }
}
