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

@Component
@RequiredArgsConstructor
@Slf4j
public class GameDataLoader {

    static final String SEASON_DIRECTORY_PREFIX = "MD";

    private final ObjectMapper objectMapper;

    public PlannerVersions loadPlannerVersions(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("Planner versions file not found: " + filePath);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(Files.readString(filePath));
        } catch (IOException e) {
            throw new GameDataLoadException(filePath, e);
        }

        try {
            return PlannerVersions.parse(root);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid planner versions file " + filePath + ": " + e.getMessage(), e);
        }
    }

    public Path seasonDirectory(Path dataDir, int version) {
        Path seasonDir = dataDir.resolve(SEASON_DIRECTORY_PREFIX + version);
        if (!Files.isDirectory(seasonDir)) {
            throw new IllegalStateException("Season " + version + " is listed in "
                    + GameDataRegistry.PLANNER_VERSIONS_FILE + " but has no data directory: " + seasonDir);
        }
        return seasonDir;
    }

    public PlannerIdMigrations loadIdMigrations(Path filePath) {
        if (!Files.exists(filePath)) {
            log.info("No id migration table at {}; normalizing with an empty table", filePath);
            return PlannerIdMigrations.EMPTY;
        }

        try {
            return PlannerIdMigrations.parse(objectMapper.readTree(Files.readString(filePath)));
        } catch (IOException | IllegalArgumentException e) {
            throw new GameDataLoadException(filePath, e);
        }
    }

    public Set<String> loadKeysFromFile(Path filePath) {
        Set<String> keys = new HashSet<>();
        forEachField(filePath, (key, value) -> keys.add(key));

        log.debug("Loaded {} keys from {}", keys.size(), filePath.getFileName());
        return keys;
    }

    /**
     * Format: { "keyword": [giftId1, giftId2, ...], ... }
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
     * Format: { "giftId": { "themePack": ["packId1", "packId2", ...], ... }, ... }
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
     * Format: { "egoId": { ..., "maxThreadspin": 4|5 }, ... }
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
