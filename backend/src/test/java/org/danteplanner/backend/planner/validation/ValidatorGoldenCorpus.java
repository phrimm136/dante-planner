package org.danteplanner.backend.planner.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.danteplanner.backend.planner.entity.PlannerType;

/**
 * The bad-input corpus whose validator output is frozen by {@link ValidatorGoldenCorpusTest}.
 *
 * <p>Every entry names one document and the category and policy it is validated under. The
 * document is assembled field by field rather than by text substitution, so field order — which
 * decides the order Jackson reports object members in, and therefore the order errors accumulate
 * in — is fixed by the corpus rather than by a search-and-replace landing where it lands.
 *
 * <p>An entry needs no discipline about which fields it omits together: the snapshot renderer
 * sorts the two messages that render a set, so the JVM's per-run iteration salt cannot reach the
 * frozen text.
 */
final class ValidatorGoldenCorpus {

    private ValidatorGoldenCorpus() {
    }

    /**
     * One planner-content document, and the arguments the content chain validates it under.
     *
     * @param name     the entry's identity in the snapshot file
     * @param category the MD category passed alongside the content
     * @param policy   how completely the document must be filled in
     * @param content  the raw content string, exactly as a client would send it
     */
    record ContentEntry(String name, String category, ValidationPolicy policy, String content) {
    }

    /**
     * One content-version claim, as a new planner declares it.
     *
     * @param name           the entry's identity in the snapshot file
     * @param plannerType    the type whose accepted versions apply
     * @param contentVersion the version declared, or null for an absent one
     */
    record VersionEntry(String name, PlannerType plannerType, Integer contentVersion) {
    }

    static final int MAX_CONTENT_SIZE_BYTES = 51200;
    static final int MAX_NOTE_SIZE_BYTES = 1024;

    static final int MD_CURRENT_VERSION = 7;
    static final String RR_AVAILABLE_VERSIONS = "1,5";

    static final Set<String> IDENTITY_IDS = sinnerScopedIds("1", "01");
    static final Set<String> EGO_IDS = union(sinnerScopedIds("2", "01"), Set.of("20502"));

    /**
     * {@code 20502} is deliberately absent: it is a known EGO whose per-EGO ceiling the registry
     * cannot answer, which is the only way to reach the maxThreadspin rejection.
     */
    static final Map<String, Integer> EGO_MAX_THREADSPIN = maxThreadspinForAllBut("20502");

    static final Set<String> EGO_GIFT_IDS = Set.of("9001", "9002", "9003", "9004", "gift_a", "gift_b");
    static final Set<String> THEME_PACK_IDS = Set.of("1001", "1002");
    static final Set<String> START_BUFF_IDS = Set.of("100", "101", "115", "200", "201", "302");

    static final Map<String, Set<String>> START_GIFT_POOLS = Map.of(
            "Combustion", Set.of("9001", "9002", "gift_a"),
            "Slash", Set.of("9003"));

    /**
     * Theme-pack availability per gift, in the shape the loader hands the registry: an empty list
     * means universal. {@code 9004} lists a pack no corpus floor selects, so the affordability
     * rejection is reached through the registry's own rule rather than around it.
     */
    static final Map<String, List<String>> EGO_GIFT_THEME_PACKS = Map.of(
            "9001", List.of(),
            "9002", List.of(),
            "9003", List.of(),
            "9004", List.of("1002"),
            "gift_a", List.of(),
            "gift_b", List.of());

    /**
     * A document over the byte ceiling, spelled out rather than derived from {@link #valid()}: the
     * rejection message quotes the byte count, so a baseline that gained a field would otherwise
     * silently rewrite this entry's frozen message.
     */
    static final String OVERSIZED_DOCUMENT = "{\"selectedKeywords\":[\"" + "x".repeat(52000)
            + "\"],\"equipment\":{},\"deploymentOrder\":[],\"floorSelections\":[],\"sectionNotes\":{}}";

    private static Set<String> sinnerScopedIds(String prefix, String suffix) {
        Set<String> ids = new HashSet<>();
        for (int sinner = 1; sinner <= 12; sinner++) {
            ids.add(prefix + String.format("%02d", sinner) + suffix);
        }
        return Set.copyOf(ids);
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> all = new HashSet<>(left);
        all.addAll(right);
        return Set.copyOf(all);
    }

    private static Map<String, Integer> maxThreadspinForAllBut(String excluded) {
        Map<String, Integer> ceilings = new HashMap<>();
        for (String egoId : EGO_IDS) {
            if (!egoId.equals(excluded)) {
                ceilings.put(egoId, 4);
            }
        }
        return Map.copyOf(ceilings);
    }

    /**
     * A planner-content document under construction, as an ordered field list.
     *
     * <p>Values are raw JSON text so a corpus entry can carry a value no object model would
     * accept — a number where a string belongs, a truncated array, a field of the wrong shape.
     */
    static final class Doc {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Doc() {
        }

        Doc with(String field, String rawJson) {
            fields.put(field, rawJson);
            return this;
        }

        Doc without(String field) {
            fields.remove(field);
            return this;
        }

        String json() {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(field.getKey()).append("\":").append(field.getValue());
                first = false;
            }
            return json.append("}").toString();
        }
    }

    /** A document every validator accepts, which each entry below spoils in exactly one way. */
    static Doc valid() {
        return new Doc()
                .with("selectedKeywords", "[]")
                .with("selectedBuffIds", "[100,201]")
                .with("selectedGiftKeyword", "\"Combustion\"")
                .with("selectedGiftIds", "[\"9001\"]")
                .with("observationGiftIds", "[\"9002\"]")
                .with("comprehensiveGiftIds", "[\"9003\"]")
                .with("equipment", equipment())
                .with("deploymentOrder", "[0,1,2,3,4,5,6,7,8,9,10,11]")
                .with("skillEAState", skillEAState())
                .with("floorSelections", "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9002\"]}]")
                .with("sectionNotes", "{}");
    }

    private static String equipment() {
        return equipmentWith(Map.of());
    }

    /**
     * The twelve sinners, with {@code replacements} substituted by sinner key. A replacement is the
     * whole entry text including its key, so an entry may rename or reshape the key it replaces; a
     * replacement mapped to the empty string drops that sinner entirely.
     */
    static String equipmentWith(Map<String, String> replacements) {
        List<String> entries = new ArrayList<>();
        for (int sinner = 1; sinner <= 12; sinner++) {
            String key = String.format("%02d", sinner);
            String replacement = replacements.get(key);
            if (replacement == null) {
                entries.add("\"" + key + "\":" + sinnerEquipment(key));
            } else if (!replacement.isEmpty()) {
                entries.add(replacement);
            }
        }
        return "{" + String.join(",", entries) + "}";
    }

    private static String sinnerEquipment(String key) {
        return "{\"identity\":{\"id\":\"1" + key + "01\",\"level\":45,\"uptie\":4},"
                + "\"egos\":{\"ZAYIN\":{\"id\":\"2" + key + "01\",\"threadspin\":4}}}";
    }

    private static String skillEAState() {
        return skillEAStateWith(Map.of());
    }

    static String skillEAStateWith(Map<String, String> replacements) {
        List<String> entries = new ArrayList<>();
        for (int sinner = 1; sinner <= 12; sinner++) {
            String key = String.format("%02d", sinner);
            String replacement = replacements.get(key);
            if (replacement == null) {
                entries.add("\"" + key + "\":{\"0\":3,\"1\":2,\"2\":1}");
            } else if (!replacement.isEmpty()) {
                entries.add(replacement);
            }
        }
        return "{" + String.join(",", entries) + "}";
    }

    private static String floors(int count, Map<Integer, String> replacements) {
        List<String> entries = new ArrayList<>();
        for (int floor = 0; floor < count; floor++) {
            String replacement = replacements.get(floor);
            entries.add(replacement == null
                    ? "{\"themePackId\":\"1001\",\"difficulty\":1}"
                    : replacement);
        }
        return "[" + String.join(",", entries) + "]";
    }

    static List<ContentEntry> contentEntries() {
        List<ContentEntry> entries = new ArrayList<>();

        entries.add(draft("category-null", null, valid()));
        entries.add(draft("category-blank", "", valid()));
        entries.add(draft("category-unknown", "20F", valid()));
        entries.add(new ContentEntry("content-null", "5F", ValidationPolicy.DRAFT, null));
        entries.add(new ContentEntry("content-blank", "5F", ValidationPolicy.DRAFT, "   "));
        entries.add(new ContentEntry("content-size-exceeded", "5F", ValidationPolicy.DRAFT,
                OVERSIZED_DOCUMENT));
        entries.add(new ContentEntry("json-malformed", "5F", ValidationPolicy.DRAFT, "not json"));
        entries.add(new ContentEntry("json-truncated", "5F", ValidationPolicy.DRAFT, "{\"selectedKeywords\":["));
        entries.add(new ContentEntry("root-is-array", "5F", ValidationPolicy.DRAFT, "[]"));
        entries.add(new ContentEntry("root-is-primitive", "5F", ValidationPolicy.DRAFT, "\"planner\""));

        entries.add(draft("unknown-field-single", valid().with("theme", "\"dark\"")));
        entries.add(draft("unknown-fields-multiple",
                valid().with("theme", "\"dark\"").with("revision", "3")));
        entries.add(draft("missing-required-single", valid().without("sectionNotes")));
        entries.add(draft("missing-required-multiple", valid()
                .without("deploymentOrder").without("floorSelections").without("sectionNotes")));
        entries.add(draft("missing-required-hash-colliding-pair",
                valid().without("selectedKeywords").without("equipment")));
        entries.add(draft("equipment-sinners-missing-hash-colliding-pair",
                valid().with("equipment", equipmentWith(Map.of("01", "", "12", "")))));

        entries.add(draft("field-type-selected-keywords", valid().with("selectedKeywords", "\"none\"")));
        entries.add(draft("field-type-deployment-order", valid().with("deploymentOrder", "\"none\"")));
        entries.add(draft("field-type-floor-selections", valid().with("floorSelections", "{}")));
        entries.add(draft("field-type-equipment", valid().with("equipment", "[]")));
        entries.add(draft("field-type-section-notes", valid().with("sectionNotes", "[]")));
        entries.add(draft("field-type-selected-buff-ids", valid().with("selectedBuffIds", "\"none\"")));
        entries.add(draft("field-type-selected-gift-ids", valid().with("selectedGiftIds", "\"none\"")));
        entries.add(draft("field-type-observation-gift-ids", valid().with("observationGiftIds", "{}")));
        entries.add(draft("field-type-comprehensive-gift-ids", valid().with("comprehensiveGiftIds", "7")));
        entries.add(draft("field-type-skill-ea-state", valid().with("skillEAState", "[]")));
        entries.add(draft("field-type-selected-gift-keyword", valid().with("selectedGiftKeyword", "123")));

        entries.add(draft("field-type-described-as-boolean", valid().with("selectedKeywords", "true")));
        entries.add(draft("field-type-described-as-null", valid().with("skillEAState", "null")));
        entries.add(draft("field-type-described-as-long-string",
                valid().with("selectedKeywords", "\"" + "y".repeat(120) + "\"")));

        entries.add(draft("note-size-exceeded", valid().with("sectionNotes",
                "{\"floor-1\":{\"text\":\"" + "n".repeat(1100) + "\"}}")));

        entries.add(draft("equipment-key-non-numeric", valid().with("equipment", equipmentWith(
                Map.of("05", "\"yi_sang\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("equipment-key-out-of-range", valid().with("equipment", equipmentWith(
                Map.of("05", "\"13\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("equipment-sinner-missing",
                valid().with("equipment", equipmentWith(Map.of("05", "")))));
        entries.add(draft("equipment-sinner-not-object",
                valid().with("equipment", equipmentWith(Map.of("05", "\"05\":[]")))));
        entries.add(draft("equipment-identity-missing", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("equipment-identity-id-blank", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"  \",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("equipment-egos-missing", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4}}")))));
        entries.add(draft("equipment-ego-type-unknown", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4},\"KETHER\":{}}}")))));
        entries.add(draft("equipment-zayin-missing", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"TETH\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("equipment-zayin-id-blank", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"\",\"threadspin\":4}}}")))));

        entries.add(draft("deployment-order-non-number", valid().with("deploymentOrder", "[0,\"abc\"]")));
        entries.add(draft("deployment-order-out-of-range", valid().with("deploymentOrder", "[-1,12]")));

        entries.add(draft("skill-state-slot-key-unknown", valid().with("skillEAState", skillEAStateWith(
                Map.of("05", "\"05\":{\"0\":3,\"1\":2,\"3\":1}")))));
        entries.add(draft("skill-state-slot-not-number", valid().with("skillEAState", skillEAStateWith(
                Map.of("05", "\"05\":{\"0\":\"3\",\"1\":2,\"2\":1}")))));
        entries.add(draft("skill-state-total-wrong", valid().with("skillEAState", skillEAStateWith(
                Map.of("05", "\"05\":{\"0\":1,\"1\":2,\"2\":1}")))));
        entries.add(draft("skill-state-sinner-missing",
                valid().with("skillEAState", skillEAStateWith(Map.of("05", "")))));
        entries.add(draft("skill-state-sinner-not-object",
                valid().with("skillEAState", skillEAStateWith(Map.of("05", "\"05\":5")))));

        entries.add(draft("identity-id-unknown", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10599\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("identity-sinner-mismatch", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10601\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("identity-level-out-of-range", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":99,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("identity-uptie-out-of-range", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":9},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("ego-id-not-string", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":20501,\"threadspin\":4}}}")))));
        entries.add(draft("ego-id-null", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":null,\"threadspin\":4}}}")))));
        entries.add(draft("ego-id-unknown", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20599\",\"threadspin\":4}}}")))));
        entries.add(draft("ego-sinner-mismatch", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20601\",\"threadspin\":4}}}")))));
        entries.add(draft("ego-threadspin-out-of-range", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":9}}}")))));
        entries.add(draft("ego-threadspin-above-ego-ceiling", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":5}}}")))));
        entries.add(draft("ego-ceiling-unknown", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4},"
                        + "\"TETH\":{\"id\":\"20502\",\"threadspin\":3}}}")))));

        entries.add(draft("scenario-mixed-type-gift-array",
                valid().with("selectedGiftIds", "[\"gift_a\",42,\"gift_a\"]")));
        entries.add(draft("selected-gift-id-unknown", valid().with("selectedGiftIds", "[\"9999\"]")));
        entries.add(draft("observation-gift-duplicate",
                valid().with("observationGiftIds", "[\"9002\",\"9002\"]")));
        entries.add(draft("observation-gift-not-string",
                valid().with("observationGiftIds", "[9002]")));
        entries.add(draft("comprehensive-gift-unknown",
                valid().with("comprehensiveGiftIds", "[\"9999\"]")));
        entries.add(draft("comprehensive-gift-enhancement-prefix-accepted",
                valid().with("comprehensiveGiftIds", "[\"19001\",\"29002\"]")));

        entries.add(draft("floor-theme-pack-unknown", valid().with("floorSelections",
                "[{\"themePackId\":\"9999\",\"difficulty\":0,\"giftIds\":[]}]")));
        entries.add(draft("floor-theme-pack-empty-accepted-as-draft", valid().with("floorSelections",
                "[{\"themePackId\":\"\",\"difficulty\":0,\"giftIds\":[]}]")));
        entries.add(publish("floor-theme-pack-empty-rejected-on-publish", valid().with("floorSelections",
                "[{\"themePackId\":\"\",\"difficulty\":0,\"giftIds\":[]}]")));
        entries.add(publish("floor-theme-pack-absent-rejected-on-publish", valid().with("floorSelections",
                "[{\"difficulty\":0,\"giftIds\":[]}]")));
        entries.add(publish("floor-difficulty-out-of-range", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":5,\"giftIds\":[]}]")));
        entries.add(publish("floor-difficulty-absent", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"giftIds\":[]}]")));
        entries.add(new ContentEntry("floor-difficulty-hard-required-on-10f", "10F", ValidationPolicy.PUBLISH,
                valid().with("floorSelections",
                        "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]}]").json()));
        entries.add(new ContentEntry("floor-difficulty-extreme-required-on-15f", "15F", ValidationPolicy.PUBLISH,
                valid().with("floorSelections", floors(11, Map.of())).json()));
        entries.add(draft("floor-sequence-gap", valid().with("floorSelections",
                "[{\"difficulty\":0,\"giftIds\":[]},{\"themePackId\":\"1002\",\"difficulty\":0,\"giftIds\":[]}]")));
        entries.add(draft("floor-gift-duplicate", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9002\",\"9002\"]}]")));
        entries.add(draft("floor-gift-not-string", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[9002]}]")));
        entries.add(draft("floor-gift-unknown", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9999\"]}]")));
        entries.add(draft("floor-gift-not-affordable", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9004\"]}]")));

        entries.add(draft("buff-count-exceeded",
                valid().with("selectedBuffIds", "[100,101,102,103,104,105,106,107,108,109,200]")));
        entries.add(draft("buff-not-number", valid().with("selectedBuffIds", "[100,\"x\"]")));
        entries.add(draft("buff-id-unknown", valid().with("selectedBuffIds", "[100,999]")));
        entries.add(draft("buff-base-id-out-of-range", valid().with("selectedBuffIds", "[115]")));
        entries.add(draft("buff-base-id-duplicate", valid().with("selectedBuffIds", "[100,200]")));

        entries.add(draft("start-gift-keyword-absent", valid().without("selectedGiftKeyword")));
        entries.add(draft("start-gift-keyword-null", valid().with("selectedGiftKeyword", "null")));
        entries.add(draft("start-gift-keyword-unknown",
                valid().with("selectedGiftKeyword", "\"Nonsense\"")));
        entries.add(draft("start-gift-outside-keyword-pool",
                valid().with("selectedGiftIds", "[\"9003\"]")));
        entries.add(draft("start-gift-duplicate",
                valid().with("selectedGiftIds", "[\"9001\",\"9001\"]")));
        entries.add(draft("start-gift-not-string", valid().with("selectedGiftIds", "[9001]")));

        // Each entry below pins a guard that skips an element without rejecting it. What is frozen
        // is the ABSENCE of an error, so a traversal helper that starts reporting one of these
        // silences fails here rather than reaching a client as a new rejection.
        entries.add(draft("silence-floor-element-not-object", valid().with("floorSelections", "[5]")));
        entries.add(draft("silence-floor-gift-ids-not-array", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":5}]")));
        entries.add(draft("silence-floor-theme-pack-absent-as-draft", valid().with("floorSelections",
                "[{\"difficulty\":0,\"giftIds\":[\"9004\"]}]")));
        entries.add(draft("silence-floor-previous-element-not-object", valid().with("floorSelections",
                "[5,{\"themePackId\":\"1002\",\"difficulty\":0,\"giftIds\":[]}]")));
        // A sixth floor under a five-floor category: traversal stops at the category's count, so
        // the defective floor past it is never looked at. Its gift is unaffordable, the deepest
        // check a floor reaches, so widening or dropping that bound surfaces here.
        entries.add(draft("silence-floor-beyond-category-count", valid().with("floorSelections",
                "[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                        + "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                        + "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                        + "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                        + "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                        + "{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9004\"]}]")));
        entries.add(draft("silence-identity-not-object", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":5,"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("silence-identity-id-not-textual", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":10501,\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("silence-identity-level-not-number", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":\"45\",\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("silence-identity-uptie-not-number", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":\"4\"},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}}")))));
        entries.add(draft("silence-egos-not-object", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":5}")))));
        entries.add(draft("silence-ego-value-not-object", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":5}}")))));
        entries.add(draft("silence-threadspin-not-number", valid().with("equipment", equipmentWith(
                Map.of("05", "\"05\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4},"
                        + "\"egos\":{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":\"4\"}}}")))));

        entries.add(draft("baseline-accepted", valid()));
        entries.add(publish("baseline-accepted-on-publish", valid()));

        return List.copyOf(entries);
    }

    static List<VersionEntry> versionEntries() {
        return List.of(
                new VersionEntry("content-version-absent", PlannerType.MIRROR_DUNGEON, null),
                new VersionEntry("content-version-rejected-for-md", PlannerType.MIRROR_DUNGEON, 99),
                new VersionEntry("content-version-rejected-for-rr", PlannerType.REFRACTED_RAILWAY, 99),
                new VersionEntry("content-version-accepted-for-md", PlannerType.MIRROR_DUNGEON, MD_CURRENT_VERSION),
                new VersionEntry("content-version-accepted-for-rr", PlannerType.REFRACTED_RAILWAY, 5));
    }

    private static ContentEntry draft(String name, Doc doc) {
        return draft(name, "5F", doc);
    }

    private static ContentEntry draft(String name, String category, Doc doc) {
        return new ContentEntry(name, category, ValidationPolicy.DRAFT, doc.json());
    }

    private static ContentEntry publish(String name, Doc doc) {
        return new ContentEntry(name, "5F", ValidationPolicy.PUBLISH, doc.json());
    }
}
