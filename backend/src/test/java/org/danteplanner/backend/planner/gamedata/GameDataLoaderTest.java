package org.danteplanner.backend.planner.gamedata;

import tools.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThatThrownBy(() -> loader.loadKeysFromFile(file))
                .isInstanceOf(GameDataLoadException.class)
                .hasMessage("Unreadable game data file: " + file);
    }

    @Test
    void loadStartGiftPools_WhenFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("startEgoGiftPools.json");
        Files.writeString(file, "not json at all");

        assertThatThrownBy(() -> loader.loadStartGiftPools(file))
                .isInstanceOf(GameDataLoadException.class)
                .hasMessage("Unreadable game data file: " + file);
    }

    @Test
    void loadKeysFromFile_WhenFileAbsent_ReturnsEmptySet(@TempDir Path dataDir) {
        assertThat(loader.loadKeysFromFile(dataDir.resolve("identitySpecList.json"))).isEmpty();
    }

    @Test
    void loadEgoMaxThreadspin_WhenFileAbsent_ReturnsEmptyMap(@TempDir Path dataDir) {
        assertThat(loader.loadEgoMaxThreadspin(dataDir.resolve("egoSpecList.json"))).isEmpty();
    }

    @Test
    void loadPlannerVersions_WhenFileIsWellFormed_ReturnsItsVersions(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("plannerVersions.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"mdAvailableVersions\":[6,7],\"rrAvailableVersions\":[1,5]}");

        assertThat(loader.loadPlannerVersions(file)).isEqualTo(new PlannerVersions(2, List.of(6, 7), List.of(1, 5)));
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

        assertThatThrownBy(() -> loader.loadPlannerVersions(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plannerVersions.json");
    }

    @Test
    void loadFloorRules_WhenFileIsWellFormed_ReturnsItsTable(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve(FLOOR_RULES_FILE);
        Files.writeString(file, "{\n  " + RULES + ",\n  \"categories\": {" + FIFTEEN_FLOORS + "," + FIVE_FLOORS + ","
                + TEN_FLOORS + "},\n  \"stages\": [\"draft\", \"publish\", \"index\"],\n  \"schemaVersion\": 1\n}\n");

        FloorRuleTable table = loader.loadFloorRules(file);

        assertThat(table.schemaVersion()).isEqualTo(1);
        assertThat(table.stages()).isEqualTo(List.of("draft", "publish", "index"));
        assertThat(table.floorCount(MDCategory.F5)).isEqualTo(5);
        assertThat(table.floorCount(MDCategory.F10)).isEqualTo(10);
        assertThat(table.floorCount(MDCategory.F15)).isEqualTo(15);
        assertThat(table.layout(MDCategory.F5).difficulties().get(4)).isEqualTo(Set.of(0, 1));
        assertThat(table.layout(MDCategory.F10).difficulties().get(9)).isEqualTo(Set.of(1));
        assertThat(table.layout(MDCategory.F15).difficulties().get(9)).isEqualTo(Set.of(1));
        assertThat(table.layout(MDCategory.F15).difficulties().get(10)).isEqualTo(Set.of(3));
        assertThat(table.stagesFor(FloorRuleTable.RuleName.NOT_REPEATED)).isEqualTo(Set.of("draft", "publish"));
        assertThat(table.stagesFor(FloorRuleTable.RuleName.NO_NORMAL_AFTER_HARD)).isEqualTo(Set.of("publish"));
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

        assertThatThrownBy(() -> loader.loadFloorRules(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FLOOR_RULES_FILE);
    }

    @Test
    void loadFloorRules_WhenFileAbsent_ThrowsNamingTheFile(@TempDir Path dataDir) {
        Path file = dataDir.resolve(FLOOR_RULES_FILE);

        assertThatThrownBy(() -> loader.loadFloorRules(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Floor rules file not found: " + file);
    }

    @Test
    void seasonDirectory_WhenListedSeasonHasNoDirectory_ThrowsNamingTheSeason(@TempDir Path dataDir) {
        assertThatThrownBy(() -> loader.seasonDirectory(dataDir, 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MD8");
    }

    @Test
    void loadIdMigrations_WhenFileAbsent_ReturnsEmptyTable(@TempDir Path dataDir) {
        assertThat(loader.loadIdMigrations(dataDir.resolve("idMigrations.json"))).isEqualTo(PlannerIdMigrations.EMPTY);
    }

    @Test
    void loadIdMigrations_WhenFileUnparseable_ThrowsGameDataLoadException(@TempDir Path dataDir) throws IOException {
        Path file = dataDir.resolve("idMigrations.json");
        Files.writeString(file, "{ \"egoGift\": ");

        assertThatThrownBy(() -> loader.loadIdMigrations(file))
                .isInstanceOf(GameDataLoadException.class)
                .hasMessage("Unreadable game data file: " + file);
    }
}
