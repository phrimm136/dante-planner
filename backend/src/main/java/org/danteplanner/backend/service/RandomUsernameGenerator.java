package org.danteplanner.backend.service;

import org.danteplanner.backend.config.AssociationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates unique usernames in the format: Faust-{Association}-{5-char suffix}.
 *
 * <p>Association selection uses time-decay weighted random:
 * <ul>
 *   <li>Newer associations (0-30 days) get 3x weight</li>
 *   <li>Recent associations (31-60 days) get 2x weight</li>
 *   <li>Old associations (61+ days) get 1x weight</li>
 * </ul>
 *
 * <p>Suffix uses 31 safe alphanumeric characters (excludes ambiguous: 0, 1, O, I, L),
 * providing ~28.6 million unique combinations (31^5).
 */
@Service
public class RandomUsernameGenerator {

    private static final Logger log = LoggerFactory.getLogger(RandomUsernameGenerator.class);

    /**
     * Safe alphanumeric characters for suffix generation.
     * Excludes ambiguous characters: 0, 1, O, I, L (both cases).
     */
    private static final String SAFE_CHARS = "23456789abcdefghjkmnpqrstuvwxyz";

    private static final int SUFFIX_LENGTH = 5;

    private final AssociationProvider associationProvider;
    private final SecureRandom secureRandom;

    public RandomUsernameGenerator(AssociationProvider associationProvider) {
        this.associationProvider = associationProvider;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Result of username generation containing keyword and suffix.
     */
    public record UsernameComponents(String keyword, String suffix) {}

    /**
     * Generate a new username with weighted random association and unique suffix.
     *
     * @return username components (keyword and suffix)
     */
    public UsernameComponents generate() {
        String keyword = selectWeightedAssociation();
        String suffix = generateSuffix();
        return new UsernameComponents(keyword, suffix);
    }

    /**
     * Select an association keyword using time-decay weighted random selection.
     * Associations with higher weights appear more frequently in the selection pool.
     *
     * @return selected association keyword
     */
    String selectWeightedAssociation() {
        List<String> associations = associationProvider.getAssociations();
        List<String> weightedPool = new ArrayList<>();

        for (String keyword : associations) {
            int weight = associationProvider.getWeight(keyword);
            for (int i = 0; i < weight; i++) {
                weightedPool.add(keyword);
            }
        }

        if (weightedPool.isEmpty()) {
            log.error("No associations configured in AssociationProvider");
            throw new IllegalStateException("No associations available for username generation");
        }

        int randomIndex = secureRandom.nextInt(weightedPool.size());
        return weightedPool.get(randomIndex);
    }

    /**
     * Generate a 5-character suffix from safe alphanumeric characters.
     *
     * @return 5-character lowercase suffix
     */
    String generateSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(SAFE_CHARS.length());
            suffix.append(SAFE_CHARS.charAt(randomIndex));
        }
        return suffix.toString();
    }
}
