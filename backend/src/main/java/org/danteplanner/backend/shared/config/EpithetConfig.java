package org.danteplanner.backend.shared.config;

import org.danteplanner.backend.user.dto.EpithetDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.time.ZoneOffset;

/**
 * Configuration for username generation epithets.
 * Tracks when each epithet was added for time-decay weighted random selection.
 * Frontend maps keywords to display names via i18n.
 *
 * <p>Selection is weighted by {@link WeightDecay}.</p>
 */
@Component
public class EpithetConfig implements EpithetProvider {

    /**
     * Epithet keywords mapped to their added dates.
     */
    private static final Map<String, LocalDate> EPITHETS = Map.ofEntries(
            Map.entry("NAIVE", LocalDate.of(2026, 1, 21)),
            Map.entry("STUPID", LocalDate.of(2026, 1, 21)),
            Map.entry("RATIONAL", LocalDate.of(2026, 1, 21)),
            Map.entry("BRILLIANT", LocalDate.of(2026, 1, 21)),
            Map.entry("UNBENDING", LocalDate.of(2026, 1, 21)),
            Map.entry("PROACTIVE", LocalDate.of(2026, 1, 21)),
            Map.entry("RESOURCEFUL", LocalDate.of(2026, 1, 21)),
            Map.entry("AUGUST", LocalDate.of(2026, 1, 21)),
            Map.entry("DIGNIFIED", LocalDate.of(2026, 1, 21)),
            Map.entry("LOVELY", LocalDate.of(2026, 1, 21)),
            Map.entry("GUILEFUL", LocalDate.of(2026, 1, 21)),
            Map.entry("ASTUTE", LocalDate.of(2026, 1, 21)),
            Map.entry("INTELLIGENT", LocalDate.of(2026, 1, 21)),
            Map.entry("CURIOUS", LocalDate.of(2026, 1, 21)),
            Map.entry("FORSAKEN", LocalDate.of(2026, 1, 21)),
            Map.entry("ZEALOUS", LocalDate.of(2026, 1, 21)),
            Map.entry("METHODICAL", LocalDate.of(2026, 1, 21)),
            Map.entry("METICULOUS", LocalDate.of(2026, 1, 21)),
            Map.entry("DILIGENT", LocalDate.of(2026, 1, 21)),
            Map.entry("POETIC", LocalDate.of(2026, 1, 21)),
            Map.entry("ELEGANT", LocalDate.of(2026, 1, 21)),
            Map.entry("THOROUGH", LocalDate.of(2026, 1, 21)),
            Map.entry("ATTUNED", LocalDate.of(2026, 1, 21)),
            Map.entry("LOYAL", LocalDate.of(2026, 1, 21)),
            Map.entry("COMPOSED", LocalDate.of(2026, 1, 21)),
            Map.entry("BLIND", LocalDate.of(2026, 1, 21))
    );

    /**
     * Get all valid epithet keywords.
     *
     * @return unmodifiable list of valid keywords
     */
    @Override
    public List<String> getEpithets() {
        return List.copyOf(EPITHETS.keySet());
    }

    /**
     * Calculate the weight for an epithet based on time-decay.
     * Newer epithets have higher weights to increase their selection probability.
     *
     * @param keyword the epithet keyword
     * @return weight value (1, 2, or 3)
     */
    @Override
    public int getWeight(String keyword) {
        return getWeight(keyword, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Calculate the weight for an epithet based on time-decay.
     * Package-private for testing.
     *
     * @param keyword the epithet keyword
     * @param referenceDate the date to calculate from
     * @return weight value (1, 2, or 3)
     */
    int getWeight(String keyword, LocalDate referenceDate) {
        return WeightDecay.weightOf(EPITHETS.get(keyword), referenceDate);
    }

    /**
     * Check if a keyword is a valid epithet.
     *
     * @param keyword the keyword to check
     * @return true if valid
     */
    public boolean isValidEpithet(String keyword) {
        return EPITHETS.containsKey(keyword);
    }

    /**
     * Get all epithets for UI selection.
     * Frontend maps keywords to display names via i18n.
     *
     * @return list of EpithetDto containing keywords
     */
    public List<EpithetDto> getEpithetsWithInfo() {
        return EPITHETS.keySet().stream()
            .map(EpithetDto::new)
            .toList();
    }
}
