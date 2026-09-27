package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.validation.PlannerIdMigrations.Kind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannerIdMigrationsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TABLE = """
            {
              "identity": { "rename": { "10199": "10101" } },
              "ego": { "rename": { "20199": "20101" }, "drop": ["20198", "20197"] },
              "egoGift": { "rename": { "9247": "9001" }, "drop": ["9248"] },
              "themePack": { "rename": { "9999": "1001" } },
              "startBuff": { "rename": { "150": "100" }, "drop": ["151"] }
            }
            """;

    private static JsonNode json(String text) throws IOException {
        return MAPPER.readTree(text);
    }

    private static PlannerIdMigrations table() throws IOException {
        return PlannerIdMigrations.parse(json(TABLE));
    }

    @Test
    void normalize_WhenGiftIsRenamed_RewritesEveryGiftFieldKeepingTheEnhancement() throws IOException {
        JsonNode content = json("""
                {"selectedGiftIds":["9247"],"observationGiftIds":["19247"],"comprehensiveGiftIds":["29247"],
                 "floorSelections":[{"themePackId":"1001","difficulty":0,"giftIds":["9247","9002"]}]}
                """);

        JsonNode normalized = table().normalize(content);

        assertThat(normalized).isEqualTo(json("""
                {"selectedGiftIds":["9001"],"observationGiftIds":["19001"],"comprehensiveGiftIds":["29001"],
                 "floorSelections":[{"themePackId":"1001","difficulty":0,"giftIds":["9001","9002"]}]}
                """));
    }

    @Test
    void normalize_WhenGiftIsDropped_RemovesItInEveryEnhancement() throws IOException {
        JsonNode content = json("""
                {"observationGiftIds":["9248","19248","9002"],
                 "floorSelections":[{"themePackId":"1001","giftIds":["29248"]}]}
                """);

        JsonNode normalized = table().normalize(content);

        assertThat(normalized).isEqualTo(json("""
                {"observationGiftIds":["9002"],"floorSelections":[{"themePackId":"1001","giftIds":[]}]}
                """));
    }

    @Test
    void normalize_WhenRenameCollidesWithAPresentId_KeepsOneCopy() throws IOException {
        JsonNode content = json("{\"observationGiftIds\":[\"9001\",\"9247\"],\"selectedBuffIds\":[150,100]}");

        JsonNode normalized = table().normalize(content);

        assertThat(normalized).isEqualTo(json("{\"observationGiftIds\":[\"9001\"],\"selectedBuffIds\":[100]}"));
    }

    @Test
    void normalize_WhenArrayRepeatsAnUnrenamedId_LeavesTheRepeatForValidation() throws IOException {
        JsonNode content = json("{\"observationGiftIds\":[\"9002\",\"9002\"]}");

        assertThat(table().normalize(content)).isEqualTo(content);
    }

    @Test
    void normalize_WhenEquipmentIdsAreMigrated_RenamesAndDropsOptionalEgoSlots() throws IOException {
        JsonNode content = json("""
                {"equipment":{"01":{"identity":{"id":"10199","uptie":4,"level":45},
                  "egos":{"ZAYIN":{"id":"20199","threadspin":4},"TETH":{"id":"20198","threadspin":4}}}}}
                """);

        JsonNode normalized = table().normalize(content);

        assertThat(normalized).isEqualTo(json("""
                {"equipment":{"01":{"identity":{"id":"10101","uptie":4,"level":45},
                  "egos":{"ZAYIN":{"id":"20101","threadspin":4}}}}}
                """));
    }

    @Test
    void normalize_WhenZayinEgoIsDropped_LeavesTheRequiredSlotForValidation() throws IOException {
        JsonNode content = json("{\"equipment\":{\"01\":{\"egos\":{\"ZAYIN\":{\"id\":\"20197\",\"threadspin\":4}}}}}");

        assertThat(table().normalize(content)).isEqualTo(content);
    }

    @Test
    void normalize_WhenThemePackAndBuffsAreMigrated_RewritesThemAndDropsAFloorGift() throws IOException {
        JsonNode content = json("""
                {"selectedBuffIds":[150,151,201],
                 "floorSelections":[{"themePackId":"9999","giftIds":[]},{"themePackId":"1002","giftIds":["9248"]}]}
                """);

        JsonNode normalized = table().normalize(content);

        assertThat(normalized).isEqualTo(json("""
                {"selectedBuffIds":[100,201],
                 "floorSelections":[{"themePackId":"1001","giftIds":[]},{"themePackId":"1002","giftIds":[]}]}
                """));
    }

    @Test
    void normalize_WhenThemePackIsListedAsDropped_LeavesTheFloorForValidation() throws IOException {
        JsonNode content = json("{\"floorSelections\":[{\"themePackId\":\"9998\",\"giftIds\":[]}]}");

        PlannerIdMigrations table = PlannerIdMigrations.parse(json("{\"themePack\":{\"drop\":[\"9998\"]}}"));

        assertThat(table.normalize(content)).isEqualTo(content);
    }

    @Test
    void normalize_WhenAppliedTwice_EqualsASingleApplication() throws IOException {
        JsonNode content = json("""
                {"selectedGiftIds":["9247","9248"],"selectedBuffIds":[150,151],
                 "equipment":{"01":{"identity":{"id":"10199"},"egos":{"TETH":{"id":"20198"}}}},
                 "floorSelections":[{"themePackId":"9998","giftIds":["19247"]}]}
                """);
        PlannerIdMigrations table = table();

        JsonNode once = table.normalize(content);

        assertThat(table.normalize(once)).isEqualTo(once);
    }

    @Test
    void normalize_WhenCalled_LeavesItsInputUntouched() throws IOException {
        JsonNode content = json("{\"selectedGiftIds\":[\"9247\"]}");
        JsonNode copy = content.deepCopy();

        table().normalize(content);

        assertThat(content).isEqualTo(copy);
    }

    @Test
    void normalize_WhenTableIsEmpty_ReturnsEqualContent() throws IOException {
        JsonNode content = json("{\"selectedGiftIds\":[\"9247\"],\"selectedBuffIds\":[150]}");

        assertThat(PlannerIdMigrations.EMPTY.normalize(content)).isEqualTo(content);
    }

    @Test
    void parse_WhenEntityTypeIsUnknown_Throws() throws IOException {
        JsonNode table = json("{\"sinner\":{\"drop\":[\"1\"]}}");

        assertThatThrownBy(() -> PlannerIdMigrations.parse(table))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown entity type 'sinner'");
    }

    @Test
    void parse_WhenDropIsNotAnArrayOfStrings_Throws() throws IOException {
        JsonNode table = json("{\"egoGift\":{\"drop\":[9248]}}");

        assertThatThrownBy(() -> PlannerIdMigrations.parse(table))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("egoGift.drop");
    }

    @Test
    void parse_WhenSectionHasUnknownField_Throws() throws IOException {
        JsonNode table = json("{\"egoGift\":{\"remove\":[\"9248\"]}}");

        assertThatThrownBy(() -> PlannerIdMigrations.parse(table))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field 'remove'");
    }

    @Test
    void inconsistenciesWith_WhenTableAgreesWithGameData_IsEmpty() throws IOException {
        assertThat(table().inconsistenciesWith(knowing(Set.of(
                "10101", "20101", "9001", "1001", "100")))).isEmpty();
    }

    @Test
    void inconsistenciesWith_WhenRenameTargetIsUnknown_NamesIt() throws IOException {
        PlannerIdMigrations table = PlannerIdMigrations.parse(json("{\"egoGift\":{\"rename\":{\"9247\":\"9999\"}}}"));

        assertThat(table.inconsistenciesWith(knowing(Set.of())))
                .containsExactly("egoGift rename 9247 -> 9999: target is not in the game data");
    }

    @Test
    void inconsistenciesWith_WhenDroppedIdStillExists_NamesIt() throws IOException {
        PlannerIdMigrations table = PlannerIdMigrations.parse(json("{\"ego\":{\"drop\":[\"20102\"]}}"));

        assertThat(table.inconsistenciesWith(knowing(Set.of("20102"))))
                .containsExactly("ego drop 20102: id is still in the game data");
    }

    @Test
    void inconsistenciesWith_WhenAnIdentityIsDropped_NamesIt() throws IOException {
        PlannerIdMigrations table = PlannerIdMigrations.parse(json("{\"identity\":{\"drop\":[\"10199\"]}}"));

        assertThat(table.inconsistenciesWith(knowing(Set.of())))
                .containsExactly("identity drop 10199: an equipped identity has no empty form; supply a rename");
    }

    @Test
    void inconsistenciesWith_WhenAThemePackIsDropped_NamesIt() throws IOException {
        PlannerIdMigrations table = PlannerIdMigrations.parse(json("{\"themePack\":{\"drop\":[\"9998\"]}}"));

        assertThat(table.inconsistenciesWith(knowing(Set.of())))
                .containsExactly("themePack drop 9998: a floor's theme pack has no empty form; supply a rename");
    }

    @Test
    void inconsistenciesWith_WhenRenameTargetIsDropped_NamesIt() throws IOException {
        PlannerIdMigrations table = PlannerIdMigrations.parse(
                json("{\"themePack\":{\"rename\":{\"9999\":\"9998\"},\"drop\":[\"9998\"]}}"));

        assertThat(table.inconsistenciesWith(knowing(Set.of())))
                .contains("themePack rename 9999 -> 9998: target is dropped");
    }

    @Test
    void inconsistenciesWith_WhenRenameTargetIsItselfRenamed_NamesIt() throws IOException {
        PlannerIdMigrations table = PlannerIdMigrations.parse(
                json("{\"ego\":{\"rename\":{\"20199\":\"20101\",\"20101\":\"20102\"}}}"));

        assertThat(table.inconsistenciesWith(knowing(Set.of("20101", "20102"))))
                .containsExactly("ego rename 20199 -> 20101: target is itself renamed");
    }

    private static Map<Kind, Predicate<String>> knowing(Set<String> ids) {
        Map<Kind, Predicate<String>> known = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            known.put(kind, ids::contains);
        }
        return known;
    }
}
