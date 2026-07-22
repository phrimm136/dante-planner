package org.danteplanner.backend.planner.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * JPA AttributeConverter for the {@code selected_keywords} JSON column.
 * Converts between {@code Set<String>} and a JSON string array, remapping legacy
 * keyword ids (see {@link #RENAME_MAP}) and dropping unknown members so a stale
 * client's planner cannot fail the sync. The authoritative keyword list is
 * {@link #VALID_KEYWORDS}. Reading is total: malformed storage yields an empty set.
 */
@Slf4j
@Converter
public class KeywordSetConverter implements AttributeConverter<Set<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    /**
     * Valid planner keywords; members outside this list are dropped on write.
     */
    public static final Set<String> VALID_KEYWORDS = Set.of(
            // Status effects
            "Combustion", "Laceration", "Vibration", "Burst", "Sinking", "Breath", "Charge",
            // Attack types
            "Slash", "Penetrate", "Hit",
            // Affinities
            "CRIMSON", "SCARLET", "AMBER", "SHAMROCK", "AZURE", "INDIGO", "VIOLET",
            // Ego gifts
            "9154",
            // Synergy keywords
            "Assemble", "KnowledgeExplored", "AaCePcBt", "SwordPlayOfTheHomeland",
            "EchoOfMansion", "TimeSuspend", "EmergencyChargeForceField", "BloodDinner",
            "BlackCloud", "RetaliationBook", "HeishouSynergy",
            "Bullet", "BlessingOfIndexPrescriptAlly", "Inspire",
            "9828", "SojiRyoshuEntangle", "DawnTeam"
    );

    /**
     * Legacy keyword aliases from pre-rename clients, mapped to their current ids.
     * Renamed via migrations V038 (ChargeLoad) and V044 (AccelBullet); an outdated
     * client still syncing the old name must be normalized before storage. Keys are
     * intentionally absent from VALID_KEYWORDS.
     */
    private static final Map<String, String> RENAME_MAP = Map.of(
            "AccelBullet", "9828",
            "ChargeLoad", "EmergencyChargeForceField"
    );

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        // Normalize stale client data: remap renamed keywords, drop anything unknown.
        Set<String> remapped = attribute.stream()
                .map(k -> RENAME_MAP.getOrDefault(k, k))
                .collect(Collectors.toSet());
        // Persistence-layer cannot reject (would fail the sync), so surface the drop in logs
        // rather than silently losing it — an unknown here means an unmigrated/unknown id slipped past the client.
        Set<String> dropped = remapped.stream()
                .filter(k -> !VALID_KEYWORDS.contains(k))
                .collect(Collectors.toSet());
        if (!dropped.isEmpty()) {
            log.warn("Dropping unknown planner keywords on write: {}", dropped);
        }
        List<String> kept = remapped.stream()
                .filter(VALID_KEYWORDS::contains)
                .sorted()
                .toList();
        if (kept.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(kept);
        } catch (Exception e) {
            log.error("Failed to serialize planner keywords {}", kept, e);
            return null;
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashSet<>();
        }
        List<String> parsed;
        try {
            parsed = MAPPER.readValue(dbData, STRING_LIST);
        } catch (Exception e) {
            log.warn("Unreadable planner keyword storage, treating as empty: {}", dbData, e);
            return new HashSet<>();
        }
        return parsed.stream()
                .map(k -> RENAME_MAP.getOrDefault(k, k))
                .filter(VALID_KEYWORDS::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
