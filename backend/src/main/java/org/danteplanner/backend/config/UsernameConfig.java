package org.danteplanner.backend.config;

import org.danteplanner.backend.dto.user.AssociationDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Configuration for username generation associations.
 * Maps association keywords to display names and tracks when each was added
 * for time-decay weighted random selection.
 *
 * <p>Weight calculation (time-decay):
 * <ul>
 *   <li>0-30 days since added: weight 3</li>
 *   <li>31-60 days since added: weight 2</li>
 *   <li>61+ days since added: weight 1</li>
 * </ul>
 */
@Component
public class UsernameConfig implements AssociationProvider {

    /**
     * Association information: display name and date added.
     */
    public record AssociationInfo(String displayName, LocalDate addedDate) {}

    private static final int WEIGHT_NEW = 3;      // 0-30 days
    private static final int WEIGHT_RECENT = 2;   // 31-60 days
    private static final int WEIGHT_OLD = 1;      // 61+ days

    private static final int TIER_NEW_DAYS = 30;
    private static final int TIER_RECENT_DAYS = 60;

    /**
     * Static mapping of association keywords to their info.
     * Keywords are stored in UPPER_SNAKE_CASE for database storage.
     * Display names are English roleplay-friendly names.
     */
    private static final Map<String, AssociationInfo> ASSOCIATIONS = Map.ofEntries(
            Map.entry("LIMBUS_COMPANY_LCB", new AssociationInfo("LCB", LocalDate.of(2024, 1, 1))),
            Map.entry("W_CORP", new AssociationInfo("WCorp", LocalDate.of(2024, 1, 1))),
            Map.entry("LOBOTOMY_BRANCH", new AssociationInfo("Lobotomy", LocalDate.of(2024, 1, 1))),
            Map.entry("N_CORP", new AssociationInfo("NCorp", LocalDate.of(2024, 1, 1))),
            Map.entry("ZWEI", new AssociationInfo("Zwei", LocalDate.of(2024, 1, 1))),
            Map.entry("SEVEN", new AssociationInfo("Seven", LocalDate.of(2024, 1, 1))),
            Map.entry("BLADE_LINEAGE", new AssociationInfo("Blade", LocalDate.of(2024, 1, 1))),
            Map.entry("WUTHERING_HEIGHTS", new AssociationInfo("Butler", LocalDate.of(2024, 1, 1))),
            Map.entry("MULTI_CRACK", new AssociationInfo("Multicrack", LocalDate.of(2024, 1, 1))),
            Map.entry("H_CORP", new AssociationInfo("Heishou", LocalDate.of(2024, 1, 1))),
            Map.entry("SHI", new AssociationInfo("Shi", LocalDate.of(2024, 1, 1)))
    );

    /**
     * Get all valid association keywords.
     *
     * @return unmodifiable list of valid keywords
     */
    @Override
    public List<String> getAssociations() {
        return List.copyOf(ASSOCIATIONS.keySet());
    }

    /**
     * Get the English display name for an association keyword.
     *
     * @param keyword the association keyword (e.g., "W_CORP")
     * @return the display name (e.g., "WCorp"), or the keyword if not found
     */
    public String getDisplayName(String keyword) {
        AssociationInfo info = ASSOCIATIONS.get(keyword);
        return info != null ? info.displayName() : keyword;
    }

    /**
     * Calculate the weight for an association based on time-decay.
     * Newer associations have higher weights to increase their selection probability.
     *
     * @param keyword the association keyword
     * @return weight value (1, 2, or 3)
     */
    @Override
    public int getWeight(String keyword) {
        return getWeight(keyword, LocalDate.now());
    }

    /**
     * Calculate the weight for an association based on time-decay.
     * Package-private for testing.
     *
     * @param keyword the association keyword
     * @param referenceDate the date to calculate from
     * @return weight value (1, 2, or 3)
     */
    int getWeight(String keyword, LocalDate referenceDate) {
        AssociationInfo info = ASSOCIATIONS.get(keyword);
        if (info == null) {
            return WEIGHT_OLD;
        }

        long daysSinceAdded = ChronoUnit.DAYS.between(info.addedDate(), referenceDate);

        if (daysSinceAdded <= TIER_NEW_DAYS) {
            return WEIGHT_NEW;
        } else if (daysSinceAdded <= TIER_RECENT_DAYS) {
            return WEIGHT_RECENT;
        } else {
            return WEIGHT_OLD;
        }
    }

    /**
     * Check if a keyword is a valid association.
     *
     * @param keyword the keyword to check
     * @return true if valid
     */
    public boolean isValidAssociation(String keyword) {
        return ASSOCIATIONS.containsKey(keyword);
    }

    /**
     * Get all associations with their display information.
     * Returns a list of all 11 associations for UI selection.
     *
     * @return list of AssociationDto containing keyword and displayName
     */
    public List<AssociationDto> getAssociationsWithInfo() {
        return ASSOCIATIONS.entrySet().stream()
            .map(entry -> new AssociationDto(entry.getKey(), entry.getValue().displayName()))
            .toList();
    }
}
