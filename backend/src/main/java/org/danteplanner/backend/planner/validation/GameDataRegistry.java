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

@Component
@Slf4j
public class GameDataRegistry {

    private static final Pattern GIFT_ENHANCEMENT_PATTERN = Pattern.compile("^[12]?(9\\d{3})$");

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

    public Integer getEgoMaxThreadspin(String id) {
        return snapshot.egoMaxThreadspin().get(id);
    }

    public boolean hasEgoGift(String id) {
        String baseId = stripGiftEnhancement(id);
        return snapshot.egoGiftIds().contains(baseId);
    }

    public boolean hasThemePack(String id) {
        return snapshot.themePackIds().contains(id);
    }

    public boolean hasStartBuff(String id) {
        return snapshot.startBuffIds().contains(id);
    }

    public Set<String> getStartGiftPool(String keyword) {
        return snapshot.startGiftPools().get(keyword);
    }

    public boolean hasStartGiftKeyword(String keyword) {
        return snapshot.startGiftPools().containsKey(keyword);
    }

    public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
        String baseId = stripGiftEnhancement(giftId);
        List<String> themePacks = snapshot.egoGiftThemePackMap().get(baseId);

        if (themePacks == null) {
            return false;
        }

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
