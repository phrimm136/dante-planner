package org.danteplanner.backend.planner.validation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry for game data IDs.
 * Single responsibility: Store and query ID existence.
 */
@Component
@Slf4j
public class GameDataRegistry {

    private static final Pattern GIFT_ENHANCEMENT_PATTERN = Pattern.compile("^[12]?(9\\d{3})$");

    /**
     * One load of the game data, as a single immutable value.
     *
     * @param identityIds         known identity IDs
     * @param egoIds              known EGO IDs
     * @param egoGiftIds          known EGO Gift base IDs
     * @param themePackIds        known theme pack IDs
     * @param startBuffIds        known start buff IDs
     * @param startGiftPools      gift IDs by start gift keyword
     * @param egoGiftThemePackMap theme pack IDs by EGO Gift base ID, empty list meaning universal
     * @param egoMaxThreadspin    max threadspin by EGO ID
     */
    private record Snapshot(
            Set<String> identityIds,
            Set<String> egoIds,
            Set<String> egoGiftIds,
            Set<String> themePackIds,
            Set<String> startBuffIds,
            Map<String, Set<String>> startGiftPools,
            Map<String, List<String>> egoGiftThemePackMap,
            Map<String, Integer> egoMaxThreadspin) {

        private static final Snapshot EMPTY = new Snapshot(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of());

        private boolean isPopulated() {
            return !identityIds.isEmpty()
                    && !egoIds.isEmpty()
                    && !egoGiftIds.isEmpty()
                    && !themePackIds.isEmpty()
                    && !startBuffIds.isEmpty()
                    && !startGiftPools.isEmpty()
                    && !egoGiftThemePackMap.isEmpty();
        }
    }

    private final GameDataLoader loader;
    private final String dataPath;

    public GameDataRegistry(
            GameDataLoader loader,
            @Value("${game.data.path:../static/data}") String dataPath) {
        this.loader = loader;
        this.dataPath = dataPath;
    }

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    @PostConstruct
    public void init() {
        refresh();
        if (!isPopulated()) {
            throw new IllegalStateException(
                    "Game data is empty or unreadable at '" + dataPath
                            + "'; every planner save would be rejected as an invalid id reference");
        }
    }

    /**
     * Reload all game data from static JSON files.
     *
     * <p>The load is assembled in full and published in one write, so a concurrent validation
     * reads either the whole previous load or the whole new one, never a mix of the two.</p>
     */
    public void refresh() {
        log.info("Loading game data from: {}", dataPath);

        Snapshot loaded = new Snapshot(
                Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "identitySpecList.json"))),
                Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "egoSpecList.json"))),
                Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "egoGiftSpecList.json"))),
                Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "themePackList.json"))),
                Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "MD6", "startBuffs.json"))),
                Map.copyOf(loader.loadStartGiftPools(Path.of(dataPath, "MD6", "startEgoGiftPools.json"))),
                Map.copyOf(loader.loadEgoGiftThemePackMap(Path.of(dataPath, "egoGiftSpecList.json"))),
                Map.copyOf(loader.loadEgoMaxThreadspin(Path.of(dataPath, "egoSpecList.json"))));

        snapshot = loaded;

        log.info("Game data loaded - identities: {}, egos: {}, gifts: {}, themePacks: {}, startBuffs: {}, giftPools: {}, giftThemePacks: {}, egoMaxThreadspin: {}",
                loaded.identityIds().size(), loaded.egoIds().size(), loaded.egoGiftIds().size(),
                loaded.themePackIds().size(), loaded.startBuffIds().size(), loaded.startGiftPools().size(),
                loaded.egoGiftThemePackMap().size(), loaded.egoMaxThreadspin().size());
    }

    public boolean hasIdentity(String id) {
        return snapshot.identityIds().contains(id);
    }

    public boolean hasEgo(String id) {
        return snapshot.egoIds().contains(id);
    }

    /**
     * Get the per-EGO max threadspin (4 or 5). Returns null if the EGO id
     * is unknown to the registry.
     */
    public Integer getEgoMaxThreadspin(String id) {
        return snapshot.egoMaxThreadspin().get(id);
    }

    /**
     * Check if EGO Gift ID exists (strips enhancement prefix).
     */
    public boolean hasEgoGift(String id) {
        String baseId = stripGiftEnhancement(id);
        return snapshot.egoGiftIds().contains(baseId);
    }

    public boolean hasThemePack(String id) {
        return snapshot.themePackIds().contains(id);
    }

    /**
     * Check if Start Buff ID exists.
     *
     * @param id Buff ID as string (e.g., "100", "205", "309")
     * @return true if ID exists in game data
     */
    public boolean hasStartBuff(String id) {
        return snapshot.startBuffIds().contains(id);
    }

    /**
     * Get gift IDs for a start gift keyword.
     *
     * @param keyword Keyword (e.g., "Combustion", "Slash")
     * @return Set of valid gift IDs for this keyword, null if keyword doesn't exist
     */
    public Set<String> getStartGiftPool(String keyword) {
        return snapshot.startGiftPools().get(keyword);
    }

    /**
     * Check if a keyword exists in start gift pools.
     */
    public boolean hasStartGiftKeyword(String keyword) {
        return snapshot.startGiftPools().containsKey(keyword);
    }

    /**
     * Check if an EGO Gift is affordable for a specific theme pack.
     *
     * @param giftId Gift ID (with or without enhancement prefix)
     * @param themePackId Theme pack ID
     * @return true if gift is available for this theme pack
     *
     * Rules:
     * - If gift's themePack list is empty → available in ALL theme packs (universal)
     * - If gift's themePack list has values → only available in those specific packs
     */
    public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
        String baseId = stripGiftEnhancement(giftId);
        List<String> themePacks = snapshot.egoGiftThemePackMap().get(baseId);

        // If gift not found in map, assume not affordable (fail-safe)
        if (themePacks == null) {
            return false;
        }

        // Empty list means universal availability
        return themePacks.isEmpty() || themePacks.contains(themePackId);
    }

    public boolean isPopulated() {
        return snapshot.isPopulated();
    }

    private String stripGiftEnhancement(String id) {
        Matcher matcher = GIFT_ENHANCEMENT_PATTERN.matcher(id);
        return matcher.matches() ? matcher.group(1) : id;
    }
}
