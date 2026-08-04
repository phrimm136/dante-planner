package org.danteplanner.backend.planner.validation;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

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
     * @return Set of keys, empty set if the file doesn't exist
     * @throws GameDataLoadException if the file exists but cannot be read or parsed
     */
    public Set<String> loadKeysFromFile(Path filePath) {
        Set<String> keys = new HashSet<>();
        forEachField(filePath, (key, value) -> keys.add(key));

        log.debug("Loaded {} keys from {}", keys.size(), filePath.getFileName());
        return keys;
    }

    /**
     * Load start gift pools from JSON file.
     * Format: { "keyword": [giftId1, giftId2, ...], ... }
     *
     * @param filePath Path to the startEgoGiftPools.json file
     * @return Map of keyword to set of gift IDs, empty map if the file doesn't exist
     * @throws GameDataLoadException if the file exists but cannot be read or parsed
     */
    public Map<String, Set<String>> loadStartGiftPools(Path filePath) {
        Map<String, Set<String>> pools = new HashMap<>();

        forEachField(filePath, (keyword, giftArray) -> {
            Set<String> giftIds = new HashSet<>();
            if (giftArray.isArray()) {
                for (JsonNode giftNode : giftArray) {
                    giftIds.add(String.valueOf(giftNode.asInt()));
                }
            }
            pools.put(keyword, giftIds);
        });

        log.debug("Loaded {} keywords from {}", pools.size(), filePath.getFileName());
        return pools;
    }

    /**
     * Load EGO Gift theme pack availability map from egoGiftSpecList.json.
     * Format: { "giftId": { "themePack": ["packId1", "packId2", ...], ... }, ... }
     *
     * @param filePath Path to the egoGiftSpecList.json file
     * @return Map of gift ID to list of theme pack IDs (empty list means universal availability)
     * @throws GameDataLoadException if the file exists but cannot be read or parsed
     */
    public Map<String, List<String>> loadEgoGiftThemePackMap(Path filePath) {
        Map<String, List<String>> themePackMap = new HashMap<>();

        forEachField(filePath, (giftId, giftNode) -> {
            if (!giftNode.isObject()) {
                return;
            }

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
        });

        log.debug("Loaded theme pack availability for {} gifts from {}", themePackMap.size(), filePath.getFileName());
        return themePackMap;
    }

    /**
     * Load per-EGO max threadspin from egoSpecList.json.
     * Format: { "egoId": { ..., "maxThreadspin": 4|5 }, ... }
     *
     * @param filePath Path to the egoSpecList.json file
     * @return Map of EGO ID to maxThreadspin, empty map if the file doesn't exist
     * @throws GameDataLoadException if the file exists but cannot be read or parsed
     */
    public Map<String, Integer> loadEgoMaxThreadspin(Path filePath) {
        Map<String, Integer> maxThreadspinMap = new HashMap<>();

        forEachField(filePath, (egoId, egoNode) -> {
            if (!egoNode.isObject()) {
                return;
            }

            JsonNode maxNode = egoNode.get("maxThreadspin");
            if (maxNode != null && maxNode.isInt()) {
                maxThreadspinMap.put(egoId, maxNode.asInt());
            }
        });

        log.debug("Loaded maxThreadspin for {} egos from {}", maxThreadspinMap.size(), filePath.getFileName());
        return maxThreadspinMap;
    }

    private void forEachField(Path filePath, BiConsumer<String, JsonNode> handler) {
        if (!Files.exists(filePath)) {
            log.warn("Data file not found: {}", filePath);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(Files.readString(filePath));
        } catch (IOException e) {
            throw new GameDataLoadException(filePath, e);
        }

        if (!root.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> field : root.properties()) {
            handler.accept(field.getKey(), field.getValue());
        }
    }
}
