package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.validation.GameDataLoadException;
import org.danteplanner.backend.planner.validation.GameDataLoader;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.danteplanner.backend.planner.validation.PlannerIdMigrations;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An unreadable game-data path is a startup failure, not a silently empty registry.
 *
 * <p>Every id lookup against an empty registry returns false, so the pod reports healthy while
 * rejecting 100% of planner saves with INVALID_ID_REFERENCE.</p>
 */
class GameDataRegistryTest {

    private static final Path STATIC_DATA = Path.of("../static/data");
    private static final String PLANNER_VERSIONS_FILE = "plannerVersions.json";

    private static void linkStaticData(Path dataDir) throws IOException {
        try (Stream<Path> entries = Files.list(STATIC_DATA)) {
            for (Path entry : entries.toList()) {
                Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
            }
        }
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

        assertThatThrownBy(registry::init).isInstanceOf(GameDataLoadException.class);
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
        assertThat(registry.hasStartBuff(7, "100")).isTrue();
        assertThat(registry.hasStartBuff(8, "100")).isFalse();
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
        assertThat(registry.hasStartBuff(6, "100")).isFalse();
        assertThat(registry.hasStartGiftKeyword(6, "Combustion")).isFalse();
        assertThat(registry.hasStartBuff(7, "100")).isTrue();
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
                "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7,8],\"rrAvailableVersions\":[1,5]}");
        GameDataRegistry registry = registryOver(dataDir);

        assertThatThrownBy(registry::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MD8");
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
    @DisplayName("an unreadable data path fails startup instead of emptying the registry")
    void init_WhenDataPathUnreadable_ThrowsIllegalState() {
        GameDataRegistry registry = new GameDataRegistry(
                new GameDataLoader(new ObjectMapper()), "/nonexistent/game-data-path");

        assertThrows(IllegalStateException.class, registry::init);
    }

    @Test
    @DisplayName("an unparseable data file fails startup")
    void init_WhenDataFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Files.writeString(dataDir.resolve("identitySpecList.json"), "{ \"10101\": ");

        GameDataRegistry registry = new GameDataRegistry(
                new GameDataLoader(new ObjectMapper()), dataDir.toString());

        assertThrows(GameDataLoadException.class, registry::init);
    }
}
