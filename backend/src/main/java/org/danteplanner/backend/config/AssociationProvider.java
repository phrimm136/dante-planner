package org.danteplanner.backend.config;

import java.util.List;

/**
 * Interface for providing association data for username generation.
 * Follows Interface Segregation Principle - clients depend only on methods they need.
 *
 * <p>Used by {@link org.danteplanner.backend.service.RandomUsernameGenerator}
 * to select weighted random associations.
 */
public interface AssociationProvider {

    /**
     * Get all valid association keywords.
     *
     * @return unmodifiable list of valid keywords (e.g., "W_CORP", "LIMBUS_COMPANY_LCB")
     */
    List<String> getAssociations();

    /**
     * Calculate the weight for an association based on time-decay.
     * Higher weights mean higher selection probability.
     *
     * @param keyword the association keyword
     * @return weight value (typically 1-3)
     */
    int getWeight(String keyword);
}
