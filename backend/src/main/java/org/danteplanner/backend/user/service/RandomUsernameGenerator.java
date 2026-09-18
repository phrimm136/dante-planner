package org.danteplanner.backend.user.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.EpithetProvider;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class RandomUsernameGenerator {


    private static final String SAFE_CHARS = "23456789abcdefghjkmnpqrstuvwxyz";

    private static final int SUFFIX_LENGTH = 5;

    private final EpithetProvider epithetProvider;
    private final SecureRandom secureRandom;

    public RandomUsernameGenerator(EpithetProvider epithetProvider) {
        this.epithetProvider = epithetProvider;
        this.secureRandom = new SecureRandom();
    }

    public record UsernameComponents(String epithet, String suffix) {}

    public UsernameComponents generate() {
        String epithet = selectWeightedEpithet();
        String suffix = generateSuffix();
        return new UsernameComponents(epithet, suffix);
    }

    String selectWeightedEpithet() {
        List<String> epithets = epithetProvider.getEpithets();
        List<String> weightedPool = new ArrayList<>();

        for (String epithet : epithets) {
            int weight = epithetProvider.getWeight(epithet);
            for (int i = 0; i < weight; i++) {
                weightedPool.add(epithet);
            }
        }

        if (weightedPool.isEmpty()) {
            log.error("No epithets configured in EpithetProvider");
            throw new IllegalStateException("No epithets available for username generation");
        }

        int randomIndex = secureRandom.nextInt(weightedPool.size());
        return weightedPool.get(randomIndex);
    }

    String generateSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(SAFE_CHARS.length());
            suffix.append(SAFE_CHARS.charAt(randomIndex));
        }
        return suffix.toString();
    }
}
