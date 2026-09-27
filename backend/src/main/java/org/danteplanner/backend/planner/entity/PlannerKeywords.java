package org.danteplanner.backend.planner.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class PlannerKeywords {

    public static final Set<String> VALID_KEYWORDS = Set.of(
            "Combustion", "Laceration", "Vibration", "Burst", "Sinking", "Breath", "Charge",
            "Slash", "Penetrate", "Hit",
            "CRIMSON", "SCARLET", "AMBER", "SHAMROCK", "AZURE", "INDIGO", "VIOLET",
            "9154",
            "Assemble", "KnowledgeExplored", "AaCePcBt", "SwordPlayOfTheHomeland",
            "EchoOfMansion", "TimeSuspend", "EmergencyChargeForceField", "BloodDinner",
            "BlackCloud", "RetaliationBook", "HeishouSynergy",
            "Bullet", "BlessingOfIndexPrescriptAlly", "Inspire",
            "9828", "SojiRyoshuEntangle", "DawnTeam"
    );

    private static final Map<String, String> RENAME_MAP = Map.of(
            "AccelBullet", "9828",
            "ChargeLoad", "EmergencyChargeForceField"
    );

    private static final Map<String, String> FILTER_RENAME_MAP = caseInsensitive(RENAME_MAP);

    private static final String CONTENT_FIELD = "selectedKeywords";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Set<String> keywords;
    private final Set<String> dropped;

    private PlannerKeywords(Set<String> keywords, Set<String> dropped) {
        this.keywords = Collections.unmodifiableSet(keywords);
        this.dropped = Collections.unmodifiableSet(dropped);
    }

    public static PlannerKeywords fromClient(Collection<String> raw) {
        return normalize(raw);
    }

    public static PlannerKeywords fromStorage(Collection<String> stored) {
        return normalize(stored);
    }

    public static PlannerKeywords fromContent(String contentJson) {
        try {
            return fromContent(MAPPER.readTree(contentJson));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Planner content is not JSON", e);
        }
    }

    public static PlannerKeywords fromContent(JsonNode root) {
        return fromSelection(root.path(CONTENT_FIELD));
    }

    public static PlannerKeywords fromSelection(JsonNode selected) {
        if (!selected.isArray()) {
            return normalize(null);
        }
        List<String> named = new ArrayList<>();
        Set<String> unnamed = new HashSet<>();
        for (JsonNode element : selected) {
            if (element.isTextual()) {
                named.add(element.asText());
            } else {
                unnamed.add(element.toString());
            }
        }
        PlannerKeywords normalized = normalize(named);
        unnamed.addAll(normalized.dropped);
        return new PlannerKeywords(normalized.keywords, unnamed);
    }

    public static String filterKeyword(String keyword) {
        return FILTER_RENAME_MAP.getOrDefault(keyword, keyword);
    }

    private static String remap(String keyword) {
        return RENAME_MAP.getOrDefault(keyword, keyword);
    }

    private static Map<String, String> caseInsensitive(Map<String, String> renames) {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        map.putAll(renames);
        return Collections.unmodifiableMap(map);
    }

    private static PlannerKeywords normalize(Collection<String> raw) {
        if (raw == null) {
            return new PlannerKeywords(Set.of(), Set.of());
        }
        Set<String> remapped = raw.stream()
                .filter(k -> k != null && !k.isEmpty())
                .map(PlannerKeywords::remap)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> dropped = remapped.stream()
                .filter(k -> !VALID_KEYWORDS.contains(k))
                .collect(Collectors.toSet());
        remapped.retainAll(VALID_KEYWORDS);
        return new PlannerKeywords(remapped, dropped);
    }

    public Set<String> asSet() {
        return new LinkedHashSet<>(keywords);
    }

    public Set<String> dropped() {
        return dropped;
    }

    public boolean isEmpty() {
        return keywords.isEmpty();
    }
}
