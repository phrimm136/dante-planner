package org.danteplanner.backend.planner.validation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.validation.PlannerIdMigrations.Kind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GameDataRegistry {

    static final Pattern GIFT_ENHANCEMENT_PATTERN = Pattern.compile("^[12]?(9\\d{3})$");

    static final String ID_MIGRATIONS_FILE = "idMigrations.json";

    static final String PLANNER_VERSIONS_FILE = "plannerVersions.json";

    private record Season(Set<String> startBuffIds, Map<String, Set<String>> startGiftPools) {

        private boolean isPopulated() {
            return !startBuffIds.isEmpty() && !startGiftPools.isEmpty();
        }
    }

    private record Snapshot(
            Set<String> identityIds,
            Set<String> egoIds,
            Set<String> egoGiftIds,
            Set<String> themePackIds,
            Map<Integer, Season> seasons,
            Map<String, List<String>> egoGiftThemePackMap,
            Map<String, Integer> egoMaxThreadspin,
            PlannerIdMigrations idMigrations,
            PlannerVersions plannerVersions) {

        private static final Snapshot EMPTY = new Snapshot(
                Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of(), PlannerIdMigrations.EMPTY, null);

        private boolean isPopulated() {
            return !identityIds.isEmpty()
                    && !egoIds.isEmpty()
                    && !egoGiftIds.isEmpty()
                    && !themePackIds.isEmpty()
                    && !seasons.isEmpty()
                    && seasons.values().stream().allMatch(Season::isPopulated)
                    && !egoGiftThemePackMap.isEmpty();
        }

        private boolean hasStartBuffInAnySeason(String id) {
            return seasons.values().stream().anyMatch(season -> season.startBuffIds().contains(id));
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

        Set<String> identityIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "identitySpecList.json")));
        Set<String> egoIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "egoSpecList.json")));
        Set<String> egoGiftIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "egoGiftSpecList.json")));
        Set<String> themePackIds = Set.copyOf(loader.loadKeysFromFile(Path.of(dataPath, "themePackList.json")));
        PlannerVersions plannerVersions = loader.loadPlannerVersions(Path.of(dataPath, PLANNER_VERSIONS_FILE));

        Map<Integer, Season> seasons = new HashMap<>();
        for (int version : plannerVersions.mdAvailableVersions()) {
            Path seasonDir = loader.seasonDirectory(Path.of(dataPath), version);
            seasons.put(version, new Season(
                    Set.copyOf(loader.loadKeysFromFile(seasonDir.resolve("startBuffs.json"))),
                    Map.copyOf(loader.loadStartGiftPools(seasonDir.resolve("startEgoGiftPools.json")))));
        }

        Snapshot loaded = new Snapshot(
                identityIds,
                egoIds,
                egoGiftIds,
                themePackIds,
                Map.copyOf(seasons),
                Map.copyOf(loader.loadEgoGiftThemePackMap(Path.of(dataPath, "egoGiftSpecList.json"))),
                Map.copyOf(loader.loadEgoMaxThreadspin(Path.of(dataPath, "egoSpecList.json"))),
                loader.loadIdMigrations(Path.of(dataPath, ID_MIGRATIONS_FILE)),
                plannerVersions);

        requireConsistentMigrations(loaded);

        snapshot = loaded;

        log.info("Game data loaded - identities: {}, egos: {}, gifts: {}, themePacks: {}, seasons: {}, giftThemePacks: {}, egoMaxThreadspin: {}, plannerVersions: {}",
                loaded.identityIds().size(), loaded.egoIds().size(), loaded.egoGiftIds().size(),
                loaded.themePackIds().size(), loaded.seasons().keySet(),
                loaded.egoGiftThemePackMap().size(), loaded.egoMaxThreadspin().size(), loaded.plannerVersions());
    }

    private void requireConsistentMigrations(Snapshot loaded) {
        Map<Kind, Predicate<String>> known = new EnumMap<>(Kind.class);
        known.put(Kind.IDENTITY, loaded.identityIds()::contains);
        known.put(Kind.EGO, loaded.egoIds()::contains);
        known.put(Kind.EGO_GIFT, loaded.egoGiftIds()::contains);
        known.put(Kind.THEME_PACK, loaded.themePackIds()::contains);
        known.put(Kind.START_BUFF, loaded::hasStartBuffInAnySeason);

        List<String> problems = loaded.idMigrations().inconsistenciesWith(known);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    ID_MIGRATIONS_FILE + " at '" + dataPath + "' disagrees with the game data: " + problems);
        }
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

    public boolean hasSeason(int version) {
        return snapshot.seasons().containsKey(version);
    }

    public PlannerVersions plannerVersions() {
        return snapshot.plannerVersions();
    }

    public boolean hasStartBuff(int version, String id) {
        Season season = snapshot.seasons().get(version);
        return season != null && season.startBuffIds().contains(id);
    }

    public Set<String> getStartGiftPool(int version, String keyword) {
        Season season = snapshot.seasons().get(version);
        return season == null ? null : season.startGiftPools().get(keyword);
    }

    public boolean hasStartGiftKeyword(int version, String keyword) {
        Season season = snapshot.seasons().get(version);
        return season != null && season.startGiftPools().containsKey(keyword);
    }

    public PlannerIdMigrations idMigrations() {
        return snapshot.idMigrations();
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
