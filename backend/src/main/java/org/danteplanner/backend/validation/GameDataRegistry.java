package org.danteplanner.backend.validation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry for game data IDs.
 * Single responsibility: Store and query ID existence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameDataRegistry {

    private static final Pattern GIFT_ENHANCEMENT_PATTERN = Pattern.compile("^[12]?(9\\d{3})$");

    private final GameDataLoader loader;

    @Value("${game.data.path:../static/data}")
    private String dataPath;

    private Set<String> identityIds = Set.of();
    private Set<String> egoIds = Set.of();
    private Set<String> egoGiftIds = Set.of();
    private Set<String> themePackIds = Set.of();
    private Set<String> startBuffIds = Set.of();
    private Map<String, Set<String>> startGiftPools = Map.of();

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * Reload all game data from static JSON files.
     */
    public void refresh() {
        log.info("Loading game data from: {}", dataPath);

        identityIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "identitySpecList.json")));
        egoIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "egoSpecList.json")));
        egoGiftIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "egoGiftSpecList.json")));
        themePackIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "themePackList.json")));
        startBuffIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "MD6", "startBuffs.json")));
        startGiftPools = Map.copyOf(loader.loadStartGiftPools(Path.of(dataPath, "MD6", "startEgoGiftPools.json")));

        log.info("Game data loaded - identities: {}, egos: {}, gifts: {}, themePacks: {}, startBuffs: {}, giftPools: {}",
                identityIds.size(), egoIds.size(), egoGiftIds.size(), themePackIds.size(), startBuffIds.size(), startGiftPools.size());
    }

    public boolean hasIdentity(String id) {
        return identityIds.contains(id);
    }

    public boolean hasEgo(String id) {
        return egoIds.contains(id);
    }

    /**
     * Check if EGO Gift ID exists (strips enhancement prefix).
     */
    public boolean hasEgoGift(String id) {
        String baseId = stripGiftEnhancement(id);
        return egoGiftIds.contains(baseId);
    }

    public boolean hasThemePack(String id) {
        return themePackIds.contains(id);
    }

    /**
     * Check if Start Buff ID exists.
     *
     * @param id Buff ID as string (e.g., "100", "205", "309")
     * @return true if ID exists in game data
     */
    public boolean hasStartBuff(String id) {
        return startBuffIds.contains(id);
    }

    /**
     * Get gift IDs for a start gift keyword.
     *
     * @param keyword Keyword (e.g., "Combustion", "Slash")
     * @return Set of valid gift IDs for this keyword, null if keyword doesn't exist
     */
    public Set<String> getStartGiftPool(String keyword) {
        return startGiftPools.get(keyword);
    }

    /**
     * Check if a keyword exists in start gift pools.
     */
    public boolean hasStartGiftKeyword(String keyword) {
        return startGiftPools.containsKey(keyword);
    }

    public boolean isPopulated() {
        return !identityIds.isEmpty()
                && !egoIds.isEmpty()
                && !egoGiftIds.isEmpty()
                && !themePackIds.isEmpty()
                && !startBuffIds.isEmpty()
                && !startGiftPools.isEmpty();
    }

    private String stripGiftEnhancement(String id) {
        if (id == null) return null;
        Matcher matcher = GIFT_ENHANCEMENT_PATTERN.matcher(id);
        return matcher.matches() ? matcher.group(1) : id;
    }
}
