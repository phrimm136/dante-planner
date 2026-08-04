package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
