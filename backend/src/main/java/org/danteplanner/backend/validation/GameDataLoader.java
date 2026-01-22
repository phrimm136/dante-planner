package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads game data IDs from static JSON files.
 * Single responsibility: File I/O operations only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameDataLoader {

    private final ObjectMapper objectMapper;

    /**
     * Load all top-level keys from a JSON object file.
     *
     * @param filePath Path to the JSON file
     * @return Set of keys, empty set if file doesn't exist or is invalid
     */
    public Set<String> loadKeysFromFile(Path filePath) {
        Set<String> keys = new HashSet<>();

        if (!Files.exists(filePath)) {
            log.warn("Data file not found: {}", filePath);
            return keys;
        }

        try {
            String content = Files.readString(filePath);
            JsonNode root = objectMapper.readTree(content);

            if (root.isObject()) {
                Iterator<String> fieldNames = root.fieldNames();
                while (fieldNames.hasNext()) {
                    keys.add(fieldNames.next());
                }
            }

            log.debug("Loaded {} keys from {}", keys.size(), filePath.getFileName());
        } catch (IOException e) {
            log.error("Failed to load data file: {}", filePath, e);
        }

        return keys;
    }

    /**
     * Load start gift pools from JSON file.
     * Format: { "keyword": [giftId1, giftId2, ...], ... }
     *
     * @param filePath Path to the startEgoGiftPools.json file
     * @return Map of keyword to set of gift IDs, empty map if file doesn't exist
     */
    public Map<String, Set<String>> loadStartGiftPools(Path filePath) {
        Map<String, Set<String>> pools = new HashMap<>();

        if (!Files.exists(filePath)) {
            log.warn("Start gift pools file not found: {}", filePath);
            return pools;
        }

        try {
            String content = Files.readString(filePath);
            JsonNode root = objectMapper.readTree(content);

            if (root.isObject()) {
                Iterator<String> keywords = root.fieldNames();
                while (keywords.hasNext()) {
                    String keyword = keywords.next();
                    JsonNode giftArray = root.get(keyword);

                    Set<String> giftIds = new HashSet<>();
                    if (giftArray.isArray()) {
                        for (JsonNode giftNode : giftArray) {
                            giftIds.add(String.valueOf(giftNode.asInt()));
                        }
                    }
                    pools.put(keyword, giftIds);
                }
            }

            log.debug("Loaded {} keywords from {}", pools.size(), filePath.getFileName());
        } catch (IOException e) {
            log.error("Failed to load start gift pools: {}", filePath, e);
        }

        return pools;
    }

    /**
     * Load EGO Gift theme pack availability map from egoGiftSpecList.json.
     * Format: { "giftId": { "themePack": ["packId1", "packId2", ...], ... }, ... }
     *
     * @param filePath Path to the egoGiftSpecList.json file
     * @return Map of gift ID to list of theme pack IDs (empty list means universal availability)
     */
    public Map<String, List<String>> loadEgoGiftThemePackMap(Path filePath) {
        Map<String, List<String>> themePackMap = new HashMap<>();

        if (!Files.exists(filePath)) {
            log.warn("EGO Gift spec file not found: {}", filePath);
            return themePackMap;
        }

        try {
            String content = Files.readString(filePath);
            JsonNode root = objectMapper.readTree(content);

            if (root.isObject()) {
                Iterator<String> giftIds = root.fieldNames();
                while (giftIds.hasNext()) {
                    String giftId = giftIds.next();
                    JsonNode giftNode = root.get(giftId);

                    if (giftNode.isObject()) {
                        JsonNode themePackNode = giftNode.get("themePack");
                        List<String> themePacks = new ArrayList<>();

                        if (themePackNode != null && themePackNode.isArray()) {
                            for (JsonNode packNode : themePackNode) {
                                if (packNode.isTextual()) {
                                    themePacks.add(packNode.asText());
                                }
                            }
                        }

                        themePackMap.put(giftId, themePacks);
                    }
                }
            }

            log.debug("Loaded theme pack availability for {} gifts from {}", themePackMap.size(), filePath.getFileName());
        } catch (IOException e) {
            log.error("Failed to load ego gift theme pack map: {}", filePath, e);
        }

        return themePackMap;
    }
}
