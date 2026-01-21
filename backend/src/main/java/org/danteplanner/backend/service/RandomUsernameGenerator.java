package org.danteplanner.backend.service;

import org.danteplanner.backend.config.EpithetProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates unique usernames in the format: {Epithet}{Sinner}#{5-char suffix}.
 *
 * <p>Epithet selection uses time-decay weighted random:
 * <ul>
 *   <li>Newer epithets (0-30 days) get 3x weight</li>
 *   <li>Recent epithets (31-60 days) get 2x weight</li>
 *   <li>Old epithets (61+ days) get 1x weight</li>
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

    private final EpithetProvider epithetProvider;
    private final SecureRandom secureRandom;

    public RandomUsernameGenerator(EpithetProvider epithetProvider) {
        this.epithetProvider = epithetProvider;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Result of username generation containing keyword and suffix.
     */
    public record UsernameComponents(String keyword, String suffix) {}

    /**
     * Generate a new username with weighted random epithet and unique suffix.
     *
     * @return username components (epithet keyword and suffix)
     */
    public UsernameComponents generate() {
        String keyword = selectWeightedEpithet();
        String suffix = generateSuffix();
        return new UsernameComponents(keyword, suffix);
    }

    /**
     * Select an epithet keyword using time-decay weighted random selection.
     * Epithets with higher weights appear more frequently in the selection pool.
     *
     * @return selected epithet keyword
     */
    String selectWeightedEpithet() {
        List<String> epithets = epithetProvider.getEpithets();
        List<String> weightedPool = new ArrayList<>();

        for (String keyword : epithets) {
            int weight = epithetProvider.getWeight(keyword);
            for (int i = 0; i < weight; i++) {
                weightedPool.add(keyword);
            }
        }

        if (weightedPool.isEmpty()) {
            log.error("No epithets configured in EpithetProvider");
            throw new IllegalStateException("No epithets available for username generation");
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
