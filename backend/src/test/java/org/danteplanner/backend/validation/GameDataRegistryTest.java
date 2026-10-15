package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.gamedata.GameDataLoadException;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.gamedata.PlannerIdMigrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An unreadable game-data path is a startup failure, not a silently empty registry.
 *
 * <p>Every id lookup against an empty registry returns false, so the pod reports healthy while
 * rejecting 100% of planner saves as unknown ids.</p>
 */
class GameDataRegistryTest {

    private static final Path STATIC_DATA = Path.of("../static/data");
    private static final String PLANNER_VERSIONS_FILE = "plannerVersions.json";
    private static final String FLOOR_RULES_FILE = "plannerFloorRules.json";

    private static void linkStaticData(Path dataDir) throws IOException {
        try (Stream<Path> entries = Files.list(STATIC_DATA)) {
            for (Path entry : entries.toList()) {
                Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
            }
        }
    }

    private static Path unlinkSeasonFile(Path dataDir, int version) throws IOException {
        Path season = dataDir.resolve("MD" + version);
        Path source = Files.readSymbolicLink(season);
        Files.delete(season);
        Files.createDirectory(season);
        try (Stream<Path> entries = Files.list(source)) {
            for (Path entry : entries.toList()) {
                if (!entry.getFileName().toString().equals("mirrorDungeon.json")) {
                    Files.createSymbolicLink(season.resolve(entry.getFileName()), entry);
                }
            }
        }
        return season.resolve("mirrorDungeon.json");
    }

    private static GameDataRegistry registryOver(Path dataDir) {
        return new GameDataRegistry(new GameDataLoader(new ObjectMapper()), dataDir.toString());
    }

    @Test
    void init_WhenIdMigrationsFileIsAbsent_LoadsAnEmptyTable(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.deleteIfExists(dataDir.resolve("idMigrations.json"));
        GameDataRegistry registry = registryOver(dataDir);

        registry.init();

        assertThat(registry.idMigrations()).isEqualTo(PlannerIdMigrations.EMPTY);
    }

    @Test
    void init_WhenIdMigrationsAgreeWithGameData_LoadsTheTable(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.deleteIfExists(dataDir.resolve("idMigrations.json"));
        Files.writeString(dataDir.resolve("idMigrations.json"),
                "{\"egoGift\":{\"rename\":{\"9247\":\"9001\"}},\"ego\":{\"drop\":[\"20199\"]}}");
        GameDataRegistry registry = registryOver(dataDir);

        registry.init();

        assertThat(registry.idMigrations().renames().get(PlannerIdMigrations.Kind.EGO_GIFT))
                .containsEntry("9247", "9001");
        assertThat(registry.idMigrations().drops().get(PlannerIdMigrations.Kind.EGO))
                .containsExactly("20199");
    }

    @Test
    void init_WhenRenameTargetIsUnknown_FailsNamingTheRename(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.deleteIfExists(dataDir.resolve("idMigrations.json"));
        Files.writeString(dataDir.resolve("idMigrations.json"),
                "{\"egoGift\":{\"rename\":{\"9247\":\"9999\"}}}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idMigrations.json")
                .hasMessageContaining("egoGift rename 9247 -> 9999: target is not in the game data");
    }

    @Test
    void init_WhenDroppedIdIsStillInTheGameData_Fails(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.deleteIfExists(dataDir.resolve("idMigrations.json"));
        Files.writeString(dataDir.resolve("idMigrations.json"), "{\"egoGift\":{\"drop\":[\"9001\"]}}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("egoGift drop 9001: id is still in the game data");
    }

    @Test
    void init_WhenAnIdentityIsDropped_FailsAskingForARename(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.deleteIfExists(dataDir.resolve("idMigrations.json"));
        Files.writeString(dataDir.resolve("idMigrations.json"), "{\"identity\":{\"drop\":[\"10199\"]}}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity drop 10199: an equipped identity has no empty form; supply a rename");
    }

    @Test
    void init_WhenIdMigrationsFileIsMalformed_FailsStartup(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.deleteIfExists(dataDir.resolve("idMigrations.json"));
        Files.writeString(dataDir.resolve("idMigrations.json"), "{\"sinner\":{}}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init).isInstanceOf(GameDataLoadException.class)
                .hasMessageEndingWith("idMigrations.json")
                .hasRootCauseMessage("unknown entity type 'sinner'; expected one of [identity, ego, egoGift, themePack, startBuff]");
    }

    private static void writePlannerVersions(Path dataDir, String json) throws IOException {
        Files.deleteIfExists(dataDir.resolve(PLANNER_VERSIONS_FILE));
        Files.writeString(dataDir.resolve(PLANNER_VERSIONS_FILE), json);
    }

    @Test
    void init_WhenPlannerVersionsListSeasons_KeysSeasonDataByVersion(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        writePlannerVersions(dataDir,
                "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[1,5]}");
        GameDataRegistry registry = registryOver(dataDir);

        registry.init();

        assertThat(registry.plannerVersions().schemaVersion()).isEqualTo(2);
        assertThat(registry.plannerVersions().mdAvailableVersions()).containsExactly(6, 7);
        assertThat(registry.plannerVersions().mdCurrentVersion()).isEqualTo(7);
        assertThat(registry.plannerVersions().rrAvailableVersions()).containsExactly(1, 5);
        assertThat(registry.hasSeason(6)).isTrue();
        assertThat(registry.hasSeason(7)).isTrue();
        assertThat(registry.hasSeason(8)).isFalse();
        assertThat(registry.startBuffIds(7)).contains("100");
        assertThat(registry.startBuffIds(8)).doesNotContain("100");
        assertThat(registry.getStartGiftPool(7, "Combustion")).contains("9001");
    }

    @Test
    void init_WhenOnlyOneSeasonIsListed_AnswersForThatSeasonAlone(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        writePlannerVersions(dataDir,
                "{\"schemaVersion\":2,\"mdAvailableVersions\":[7],\"rrAvailableVersions\":[1,5]}");
        assertThat(dataDir.resolve("MD6")).isDirectory();
        GameDataRegistry registry = registryOver(dataDir);

        registry.init();

        assertThat(registry.hasSeason(6)).isFalse();
        assertThat(registry.startBuffIds(6)).doesNotContain("100");
        assertThat(registry.hasStartGiftKeyword(6, "Combustion")).isFalse();
        assertThat(registry.startBuffIds(7)).contains("100");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"schemaVersion\":2,\"mdAvailableVersions\":[7,6],\"rrAvailableVersions\":[1,5]}",
            "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,6],\"rrAvailableVersions\":[1,5]}",
            "{\"schemaVersion\":2,\"mdAvailableVersions\":[],\"rrAvailableVersions\":[1,5]}",
            "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[5,1]}"})
    void init_WhenAVersionListIsNotStrictlyAscending_FailsNamingTheFile(String versions, @TempDir Path dataDir)
            throws IOException {
        linkStaticData(dataDir);
        writePlannerVersions(dataDir, versions);
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PLANNER_VERSIONS_FILE);
    }

    @Test
    void init_WhenAListedSeasonHasNoDataDirectory_FailsNamingTheSeason(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        writePlannerVersions(dataDir,
                "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7,8,9],\"rrAvailableVersions\":[1,5]}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MD9");
    }

    @Test
    void init_WhenStaticDataIsLoaded_ReadsEachSeasonsIdentityCap(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        GameDataRegistry registry = registryOver(dataDir);

        registry.init();

        assertThat(registry.identityMaxLevel(6)).isEqualTo(55);
        assertThat(registry.identityMaxLevel(7)).isEqualTo(60);
        assertThat(registry.identityMaxLevel(8)).isEqualTo(65);
    }

    @Test
    void init_WhenASeasonHasNoMirrorDungeonFile_FailsNamingTheFile(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        unlinkSeasonFile(dataDir, 7);
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mirror dungeon season file not found: " + dataDir.resolve("MD7/mirrorDungeon.json"));
    }

    @Test
    void init_WhenASeasonFileHoldsAnotherVersion_FailsNamingTheFileAndBothVersions(@TempDir Path dataDir)
            throws IOException {
        linkStaticData(dataDir);
        Files.writeString(unlinkSeasonFile(dataDir, 7),
                "{\"version\":6,\"identityMaxLevel\":60,\"nameLocalizeId\":\"mirrordungeon_name_7\"}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(dataDir.resolve("MD7/mirrorDungeon.json")
                        + " holds version 6 but sits in the directory of season 7");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"version\":7,\"identityMaxLevel\":0,\"nameLocalizeId\":\"mirrordungeon_name_7\"}",
            "{\"version\":7,\"nameLocalizeId\":\"mirrordungeon_name_7\"}",
            "{\"version\":7,\"identityMaxLevel\":\"60\",\"nameLocalizeId\":\"mirrordungeon_name_7\"}"})
    void init_WhenASeasonFileHasNoPositiveIdentityCap_FailsNamingTheFile(String season, @TempDir Path dataDir)
            throws IOException {
        linkStaticData(dataDir);
        Files.writeString(unlinkSeasonFile(dataDir, 7), season);
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Invalid mirror dungeon season file " + dataDir.resolve("MD7/mirrorDungeon.json"))
                .hasMessageContaining("identityMaxLevel");
    }

    @Test
    void init_WhenPlannerVersionsFileIsAbsent_FailsNamingTheFile(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.delete(dataDir.resolve(PLANNER_VERSIONS_FILE));
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PLANNER_VERSIONS_FILE);
    }

    @Test
    void init_WhenFloorRulesFileIsAbsent_FailsNamingTheFile(@TempDir Path dataDir) throws IOException {
        linkStaticData(dataDir);
        Files.delete(dataDir.resolve(FLOOR_RULES_FILE));
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FLOOR_RULES_FILE);
    }

    @Test
    void init_WhenDataPathUnreadable_ThrowsIllegalState() {
        GameDataRegistry registry = new GameDataRegistry(
                new GameDataLoader(new ObjectMapper()), "/nonexistent/game-data-path");

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Planner versions file not found: /nonexistent/game-data-path/plannerVersions.json");
    }

    @Test
    void init_WhenDataFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Files.writeString(dataDir.resolve("identitySpecList.json"), "{ \"10101\": ");

        GameDataRegistry registry = new GameDataRegistry(
                new GameDataLoader(new ObjectMapper()), dataDir.toString());

        assertThatThrownBy(registry::init)
                .isInstanceOf(GameDataLoadException.class)
                .hasMessage("Unreadable game data file: " + dataDir.resolve("identitySpecList.json"));
    }
}
