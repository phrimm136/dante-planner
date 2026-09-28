package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDataLoaderTest {

    private static final String FLOOR_RULES_FILE = "plannerFloorRules.json";
    private static final String FIVE_FLOORS = "\"5F\":{\"floorCount\":5,\"difficulties\":[[0,1],[0,1],[0,1],[0,1],[0,1]]}";
    private static final String TEN_FLOORS = "\"10F\":{\"floorCount\":10,\"difficulties\":[" + "[1],".repeat(9) + "[1]]}";
    private static final String FIFTEEN_FLOORS = "\"15F\":{\"floorCount\":15,\"difficulties\":["
            + "[1],".repeat(10) + "[3],".repeat(4) + "[3]]}";
    private static final String RULES = "\"rules\":{\"everyFloorPresent\":[\"publish\"],\"themePackPresent\":[\"publish\"],"
            + "\"notRepeated\":[\"draft\",\"publish\"],\"sequence\":[\"draft\",\"publish\"],"
            + "\"difficultyInRange\":[\"publish\"],\"noNormalAfterHard\":[\"publish\"],"
            + "\"giftUnique\":[\"draft\",\"publish\"]}";
    private static final String FLOOR_RULES = "{\"schemaVersion\":1,\"stages\":[\"draft\",\"publish\",\"index\"],"
            + "\"categories\":{" + FIVE_FLOORS + "," + TEN_FLOORS + "," + FIFTEEN_FLOORS + "}," + RULES + "}";

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
    void loadFloorRules_WhenFileIsWellFormed_ReturnsItsTable(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve(FLOOR_RULES_FILE);
        Files.writeString(file, "{\n  " + RULES + ",\n  \"categories\": {" + FIFTEEN_FLOORS + "," + FIVE_FLOORS + ","
                + TEN_FLOORS + "},\n  \"stages\": [\"draft\", \"publish\", \"index\"],\n  \"schemaVersion\": 1\n}\n");

        FloorRuleTable table = loader.loadFloorRules(file);

        assertEquals(1, table.schemaVersion());
        assertEquals(List.of("draft", "publish", "index"), table.stages());
        assertEquals(5, table.floorCount(MDCategory.F5));
        assertEquals(10, table.floorCount(MDCategory.F10));
        assertEquals(15, table.floorCount(MDCategory.F15));
        assertEquals(Set.of(0, 1), table.allowedDifficulties(MDCategory.F5, 4));
        assertEquals(Set.of(1), table.allowedDifficulties(MDCategory.F10, 9));
        assertEquals(Set.of(1), table.allowedDifficulties(MDCategory.F15, 9));
        assertEquals(Set.of(3), table.allowedDifficulties(MDCategory.F15, 10));
        assertEquals(Set.of("draft", "publish"), table.stagesFor("notRepeated"));
        assertEquals(Set.of("publish"), table.stagesFor("noNormalAfterHard"));
    }

    static Stream<String> floorRulesBreakingTheShape() {
        return Stream.of(
                FLOOR_RULES.replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"version\":1,"),
                FLOOR_RULES.replace("\"5F\":{\"floorCount\":5,", "\"5F\":{\"label\":\"5F\",\"floorCount\":5,"),
                FLOOR_RULES.replace("\"floorCount\":5,", "\"floorCount\":4,"),
                FLOOR_RULES.replace("\"giftUnique\":[\"draft\",\"publish\"]", "\"giftUnique\":[\"draft\",\"save\"]"),
                FLOOR_RULES.replace("\"difficulties\":[[0,1],", "\"difficulties\":[[],"),
                FLOOR_RULES.replace("\"difficulties\":[[0,1],", "\"difficulties\":[[0,4],"),
                FLOOR_RULES.replace("\"difficulties\":[[0,1],", "\"difficulties\":[[-1,1],"),
                FLOOR_RULES.replace("\"difficulties\":[[0,1],", "\"difficulties\":[[1,1],"),
                FLOOR_RULES.replace("\"categories\":{", "\"categories\":{\"20F\":{\"floorCount\":1,\"difficulties\":[[1]]},"),
                FLOOR_RULES.replace("," + FIFTEEN_FLOORS, ""),
                FLOOR_RULES.replace(FIVE_FLOORS, "\"5F\":{\"floorCount\":0,\"difficulties\":[]}"),
                FLOOR_RULES.replace("\"sequence\":", "\"order\":"),
                FLOOR_RULES.replace("\"stages\":[\"draft\",\"publish\",\"index\"]", "\"stages\":[]"));
    }

    @ParameterizedTest
    @MethodSource("floorRulesBreakingTheShape")
    void loadFloorRules_WhenFileBreaksTheShape_ThrowsNamingTheFile(String floorRules, @TempDir Path dataDir)
            throws IOException {
        Path file = dataDir.resolve(FLOOR_RULES_FILE);
        Files.writeString(file, floorRules);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> loader.loadFloorRules(file));
        assertTrue(ex.getMessage().contains(FLOOR_RULES_FILE), ex.getMessage());
    }

    @Test
    void loadFloorRules_WhenFileAbsent_ThrowsNamingTheFile(@TempDir Path dataDir) {
        Path file = dataDir.resolve(FLOOR_RULES_FILE);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> loader.loadFloorRules(file));
        assertEquals("Floor rules file not found: " + file, ex.getMessage());
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
