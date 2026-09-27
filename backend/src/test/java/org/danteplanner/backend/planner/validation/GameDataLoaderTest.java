package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDataLoaderTest {

    private final GameDataLoader loader = new GameDataLoader(new ObjectMapper());

    @Test
    void loadKeysFromFile_WhenFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("identitySpecList.json");
        Files.writeString(file, "{ \"10101\": ");

        assertThrows(GameDataLoadException.class, () -> loader.loadKeysFromFile(file));
    }

    @Test
    void loadStartGiftPools_WhenFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("startEgoGiftPools.json");
        Files.writeString(file, "not json at all");

        assertThrows(GameDataLoadException.class, () -> loader.loadStartGiftPools(file));
    }

    @Test
    void loadKeysFromFile_WhenFileAbsent_ReturnsEmptySet(@TempDir Path dataDir) {
        assertTrue(loader.loadKeysFromFile(dataDir.resolve("identitySpecList.json")).isEmpty());
    }

    @Test
    void loadEgoMaxThreadspin_WhenFileAbsent_ReturnsEmptyMap(@TempDir Path dataDir) {
        assertTrue(loader.loadEgoMaxThreadspin(dataDir.resolve("egoSpecList.json")).isEmpty());
    }

    @Test
    void loadPlannerVersions_WhenFileIsWellFormed_ReturnsItsVersions(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("plannerVersions.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[1,5]}");

        assertEquals(new PlannerVersions(2, List.of(6, 7), List.of(1, 5)), loader.loadPlannerVersions(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[1,5],\"mdCurrentVersion\":7}",
            "{\"schemaVersion\":0,\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[1,5]}",
            "{\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[1,5]}",
            "{\"schemaVersion\":2,\"rrAvailableVersions\":[1,5]}",
            "{\"schemaVersion\":2,\"mdAvailableVersions\":[\"6\",7],\"rrAvailableVersions\":[1,5]}"})
    void loadPlannerVersions_WhenFileBreaksTheShape_ThrowsNamingTheFile(String versions, @TempDir Path dataDir)
            throws IOException {
        Path file = dataDir.resolve("plannerVersions.json");
        Files.writeString(file, versions);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> loader.loadPlannerVersions(file));
        assertTrue(ex.getMessage().contains("plannerVersions.json"), ex.getMessage());
    }

    @Test
    void seasonDirectory_WhenListedSeasonHasNoDirectory_ThrowsNamingTheSeason(@TempDir Path dataDir) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> loader.seasonDirectory(dataDir, 8));
        assertTrue(ex.getMessage().contains("MD8"), ex.getMessage());
    }

    @Test
    void loadIdMigrations_WhenFileAbsent_ReturnsEmptyTable(@TempDir Path dataDir) {
        assertEquals(PlannerIdMigrations.EMPTY, loader.loadIdMigrations(dataDir.resolve("idMigrations.json")));
    }

    @Test
    void loadIdMigrations_WhenFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("idMigrations.json");
        Files.writeString(file, "{ \"egoGift\": ");

        assertThrows(GameDataLoadException.class, () -> loader.loadIdMigrations(file));
    }
}
