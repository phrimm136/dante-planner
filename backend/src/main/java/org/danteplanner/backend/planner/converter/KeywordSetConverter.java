package org.danteplanner.backend.planner.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * JPA AttributeConverter for the {@code selected_keywords} MySQL SET column.
 * Converts between {@code Set<String>} and the comma-separated SET storage form,
 * remapping legacy keyword ids (see {@link #RENAME_MAP}) and dropping members no longer
 * in the SET so a stale client's planner cannot fail the sync. The authoritative keyword
 * list is {@link #VALID_KEYWORDS}.
 */
@Slf4j
@Converter
public class KeywordSetConverter implements AttributeConverter<Set<String>, String> {

    /**
     * Valid keywords for the SET column - must match MySQL SET definition.
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
     * Renamed via migrations V038 (ChargeLoad) and V044 (AccelBullet); the old members
     * were dropped from the SET column, so an outdated client still syncing the old name
     * must be normalized before storage. Keys are intentionally absent from VALID_KEYWORDS.
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
        // Normalize stale client data: remap renamed keywords, drop anything no longer a
        // SET member. Mirrors convertToEntityAttribute's read tolerance so a pre-rename
        // keyword from an outdated client cannot fail the whole planner sync.
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
        String csv = remapped.stream()
                .filter(VALID_KEYWORDS::contains)
                .sorted()
                .collect(Collectors.joining(","));
        return csv.isEmpty() ? null : csv;
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashSet<>();
        }
        // Parse comma-separated MySQL SET values
        return Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(VALID_KEYWORDS::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
