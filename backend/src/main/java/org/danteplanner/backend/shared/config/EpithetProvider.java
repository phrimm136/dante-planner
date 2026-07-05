package org.danteplanner.backend.shared.config;

import java.util.List;

/**
 * Interface for providing epithet data for username generation.
 * Follows Interface Segregation Principle - clients depend only on methods they need.
 *
 * <p>Used by {@link org.danteplanner.backend.user.service.RandomUsernameGenerator}
 * to select weighted random epithets.
 */
public interface EpithetProvider {

    /**
     * Get all valid epithet keywords.
     *
     * @return unmodifiable list of valid keywords (e.g., "NAIVE", "BRILLIANT")
     */
    List<String> getEpithets();

    /**
     * Calculate the weight for an epithet based on time-decay.
     * Higher weights mean higher selection probability.
     *
     * @param keyword the epithet keyword
     * @return weight value (typically 1-3)
     */
    int getWeight(String keyword);
}
