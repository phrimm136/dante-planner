package org.danteplanner.backend.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA AttributeConverter for MySQL SET column type.
 * Converts between Set<String> and comma-separated String for storage.
 *
 * Valid keywords (28 total):
 * - Status effects: Combustion, Laceration, Vibration, Burst, Sinking, Breath, Charge
 * - Attack types: Slash, Penetrate, Hit
 * - Affinities: CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET
 * - Ego gifts: 9154
 * - Synergy keywords: Assemble, KnowledgeExplored, CoverAttack, SwordPlayOfTheHomeland,
 *                     EchoOfMansion, TimeSuspend, ChargeLoad, BloodDinner,
 *                     BlackCloud, RetaliationBook, HeishouSynergy
 */
@Converter
public class KeywordSetConverter implements AttributeConverter<Set<String>, String> {

    /**
     * Valid keywords for the SET column - must match MySQL SET definition in V031 migration.
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
            "EchoOfMansion", "TimeSuspend", "ChargeLoad", "BloodDinner",
            "BlackCloud", "RetaliationBook", "HeishouSynergy"
    );

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        // Validate all keywords before storing - fail fast on invalid
        Set<String> invalidKeywords = attribute.stream()
                .filter(k -> !VALID_KEYWORDS.contains(k))
                .collect(Collectors.toSet());
        if (!invalidKeywords.isEmpty()) {
            throw new IllegalArgumentException("Invalid keywords: " + invalidKeywords);
        }
        // MySQL SET stores as comma-separated values
        return attribute.stream()
                .sorted()
                .collect(Collectors.joining(","));
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
