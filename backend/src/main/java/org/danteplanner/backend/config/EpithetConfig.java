package org.danteplanner.backend.config;

import org.danteplanner.backend.dto.user.EpithetDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Configuration for username generation epithets.
 * Tracks when each epithet was added for time-decay weighted random selection.
 * Frontend maps keywords to display names via i18n.
 *
 * <p>Weight calculation (time-decay):
 * <ul>
 *   <li>0-30 days since added: weight 3</li>
 *   <li>31-60 days since added: weight 2</li>
 *   <li>61+ days since added: weight 1</li>
 * </ul>
 */
@Component
public class EpithetConfig implements EpithetProvider {

    private static final int WEIGHT_NEW = 3;      // 0-30 days
    private static final int WEIGHT_RECENT = 2;   // 31-60 days
    private static final int WEIGHT_OLD = 1;      // 61+ days

    private static final int TIER_NEW_DAYS = 30;
    private static final int TIER_RECENT_DAYS = 60;

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
        return getWeight(keyword, LocalDate.now());
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
        LocalDate addedDate = EPITHETS.get(keyword);
        if (addedDate == null) {
            return WEIGHT_OLD;
        }

        long daysSinceAdded = ChronoUnit.DAYS.between(addedDate, referenceDate);

        if (daysSinceAdded <= TIER_NEW_DAYS) {
            return WEIGHT_NEW;
        } else if (daysSinceAdded <= TIER_RECENT_DAYS) {
            return WEIGHT_RECENT;
        } else {
            return WEIGHT_OLD;
        }
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
