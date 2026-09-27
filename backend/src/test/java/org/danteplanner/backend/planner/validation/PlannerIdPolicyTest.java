package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannerIdPolicyTest {

    private static final Path STATIC_DATA = Path.of("../static/data");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CURRENT_SEASON = 7;
    private static final int PREVIOUS_SEASON = 6;

    @TempDir
    private Path dataDir;

    private void linkStaticData() throws IOException {
        try (Stream<Path> entries = Files.list(STATIC_DATA)) {
            for (Path entry : entries.toList()) {
                Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
            }
        }
    }

    private PlannerContentValidator validatorOver(String idMigrations) throws IOException {
        linkStaticData();
        if (idMigrations != null) {
            Files.writeString(dataDir.resolve(GameDataRegistry.ID_MIGRATIONS_FILE), idMigrations);
        }
        return validatorOverDataDir();
    }

    private PlannerContentValidator validatorOverDataDir() {
        GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(MAPPER), dataDir.toString());
        registry.init();
        return new PlannerContentValidator(
                new StructuralValidator(MAPPER,
                        ValidatorGoldenCorpus.MAX_CONTENT_SIZE_BYTES, ValidatorGoldenCorpus.MAX_NOTE_SIZE_BYTES),
                new CategoryValidator(),
                new EquipmentValidator(),
                new SkillStateValidator(),
                new IdReferenceValidator(registry, new SinnerIdValidator()),
                new StartBuffValidator(registry),
                registry);
    }

    private static ObjectNode validContent() throws IOException {
        return (ObjectNode) MAPPER.readTree(TestDataFactory.VALID_CONTENT);
    }

    private static PlannerValidationException rejection(Runnable validation) {
        try {
            validation.run();
        } catch (PlannerValidationException ex) {
            return ex;
        }
        throw new AssertionError("validation accepted the content");
    }

    @Test
    void validate_WhenContentHoldsARenamedGift_StoresTheReplacement() throws IOException {
        PlannerContentValidator validator = validatorOver("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
        ObjectNode content = validContent();
        content.putArray("observationGiftIds").add("9247").add("19247");

        String stored = validator.validate(content.toString(), "5F", CURRENT_SEASON);

        assertThat(MAPPER.readTree(stored).path("observationGiftIds"))
                .isEqualTo(MAPPER.createArrayNode().add("9002").add("19002"));
    }

    @Test
    void validate_WhenContentHoldsADroppedEgo_StoresTheSlotRemoved() throws IOException {
        PlannerContentValidator validator = validatorOver("{\"ego\":{\"drop\":[\"20199\"]}}");
        ObjectNode content = validContent();
        ObjectNode egos = (ObjectNode) content.path("equipment").path("01").path("egos");
        egos.putObject("TETH").put("id", "20199").put("threadspin", 4);

        String stored = validator.validate(content.toString(), "5F", CURRENT_SEASON);

        assertThat(MAPPER.readTree(stored).path("equipment").path("01").path("egos").has("TETH")).isFalse();
        assertThat(MAPPER.readTree(stored)).isEqualTo(validContent());
    }

    @Test
    void validate_WhenNoIdIsMigrated_ReturnsTheContentVerbatim() throws IOException {
        PlannerContentValidator validator = validatorOver("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");

        String stored = validator.validate(TestDataFactory.VALID_CONTENT, "5F", CURRENT_SEASON);

        assertThat(stored).isSameAs(TestDataFactory.VALID_CONTENT);
    }

    @ParameterizedTest
    @EnumSource(ValidationPolicy.class)
    void validate_WhenIdIsNeitherKnownNorMigrated_RejectsTheReference(ValidationPolicy policy) throws IOException {
        PlannerContentValidator validator = validatorOver("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
        ObjectNode content = validContent();
        content.putArray("observationGiftIds").add("9899");

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, policy));

        assertThat(ex.getSubErrors()).extracting(ValidationError::code).containsExactly("GIFT_UNKNOWN_ID");
        assertThat(ex.getSubErrors().get(0).message()).contains("9899");
    }

    @Test
    void validate_WhenPlannerDeclaresASeason_ChecksStartBuffsAgainstThatSeason() throws IOException {
        PlannerContentValidator validator = validatorWithPreviousSeasonLackingBuff("201");
        String content = TestDataFactory.VALID_CONTENT;

        String stored = validator.validate(content, "5F", CURRENT_SEASON);
        PlannerValidationException ex = rejection(() -> validator.validate(content, "5F", PREVIOUS_SEASON));

        assertThat(stored).isSameAs(content);
        assertThat(ex.getSubErrors()).extracting(ValidationError::code).containsExactly("START_BUFF_UNKNOWN_ID");
        assertThat(ex.getSubErrors().get(0).message()).contains("201");
    }

    @Test
    void validate_WhenPlannerDeclaresASeasonWithNoData_RejectsWithUnknownContentVersion() throws IOException {
        PlannerContentValidator validator = validatorOver(null);

        assertThatThrownBy(() -> validator.validate(TestDataFactory.VALID_CONTENT, "5F", 99))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> assertThat(ex.getSubErrors())
                        .extracting(ValidationError::code)
                        .containsExactly("UNKNOWN_CONTENT_VERSION"));
    }

    @ParameterizedTest
    @EnumSource(ValidationPolicy.class)
    void validate_WhenZayinSlotHoldsADroppedEgo_RejectsItAsAnUnknownEgo(ValidationPolicy policy) throws IOException {
        PlannerContentValidator validator = validatorOver("{\"ego\":{\"drop\":[\"20199\"]}}");
        ObjectNode content = validContent();
        ((ObjectNode) content.path("equipment").path("01").path("egos").path("ZAYIN")).put("id", "20199");

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, policy));

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getSubErrors()).extracting(ValidationError::code).containsExactly("EGO_UNKNOWN_ID");
        assertThat(ex.getSubErrors().get(0).message()).contains("20199");
    }

    @Test
    void validate_WhenAFloorHasNoThemePack_RejectsThePublishAndAcceptsTheDraft() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        ((ArrayNode) content.path("floorSelections")).set(4, MAPPER.readTree("{\"difficulty\":0,\"giftIds\":[]}"));

        String stored = validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.DRAFT);
        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.PUBLISH));

        assertThat(stored).isEqualTo(content.toString());
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getSubErrors()).extracting(ValidationError::code).containsExactly("FLOOR_MISSING_THEME_PACK");
    }

    @Test
    void validate_WhenAFiveFloorPlannerPublishesThreeFloors_ReportsTheTwoMissingFloors() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        content.set("floorSelections", completeFloors(3));

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.PUBLISH));

        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[3] must have a theme pack selected"),
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[4] must have a theme pack selected"));
    }

    @Test
    void validate_WhenAFloorEntryIsNullOnPublish_ReportsItAsAMissingFloor() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        ArrayNode floors = completeFloors(5);
        floors.set(1, NullNode.getInstance());
        content.set("floorSelections", floors);

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.PUBLISH));

        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[1] must have a theme pack selected"));
    }

    @Test
    void validate_WhenADraftHoldsFewerFloorsThanItsCategory_StoresIt() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        content.set("floorSelections", completeFloors(3));

        String stored = validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.DRAFT);

        assertThat(stored).isEqualTo(content.toString());
    }

    @Test
    void validate_WhenFloorSelectionsIsEmptyOnPublish_ReportsEveryFloorMissing() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        content.set("floorSelections", completeFloors(0));

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.PUBLISH));

        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[0] must have a theme pack selected"),
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[1] must have a theme pack selected"),
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[2] must have a theme pack selected"),
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[3] must have a theme pack selected"),
                new ValidationError("FLOOR_MISSING_THEME_PACK", "floorSelections[4] must have a theme pack selected"));
    }

    @ParameterizedTest
    @EnumSource(ValidationPolicy.class)
    void validate_WhenAFloorRepeatsAnEarlierThemePack_ReportsTheRepeat(ValidationPolicy policy) throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        content.set("floorSelections", floorsOn("1001", "1002", "1001", "1003", "1004"));

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, policy));

        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("FLOOR_DUPLICATE_THEME_PACK",
                "floorSelections[2].themePackId repeats theme pack '1001' from floorSelections[0]"));
    }

    @Test
    void validate_WhenThreeFloorsShareAThemePack_ReportsEveryRepeatAgainstTheFirst() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        content.set("floorSelections", floorsOn("1001", "1001", "1001"));

        PlannerValidationException ex = rejection(
                () -> validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.DRAFT));

        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("FLOOR_DUPLICATE_THEME_PACK",
                        "floorSelections[1].themePackId repeats theme pack '1001' from floorSelections[0]"),
                new ValidationError("FLOOR_DUPLICATE_THEME_PACK",
                        "floorSelections[2].themePackId repeats theme pack '1001' from floorSelections[0]"));
    }

    @Test
    void validate_WhenTwoDraftFloorsHaveNoThemePack_StoresThem() throws IOException {
        PlannerContentValidator validator = validatorOver(null);
        ObjectNode content = validContent();
        content.set("floorSelections",
                MAPPER.readTree("[{\"difficulty\":0,\"giftIds\":[]},{\"difficulty\":0,\"giftIds\":[]}]"));

        String stored = validator.validate(content.toString(), "5F", CURRENT_SEASON, ValidationPolicy.DRAFT);

        assertThat(stored).isEqualTo(content.toString());
    }

    private static ArrayNode floorsOn(String... themePackIds) {
        ArrayNode floors = MAPPER.createArrayNode();
        for (String themePackId : themePackIds) {
            floors.addObject().put("themePackId", themePackId).put("difficulty", 0).putArray("giftIds");
        }
        return floors;
    }

    private static ArrayNode completeFloors(int count) {
        ArrayNode floors = MAPPER.createArrayNode();
        for (int floor = 0; floor < count; floor++) {
            floors.addObject()
                    .put("themePackId", String.valueOf(1001 + floor))
                    .put("difficulty", 0)
                    .putArray("giftIds");
        }
        return floors;
    }

    private PlannerContentValidator validatorWithPreviousSeasonLackingBuff(String buffId) throws IOException {
        linkStaticData();
        Path previous = dataDir.resolve("MD" + PREVIOUS_SEASON);
        Path source = STATIC_DATA.resolve("MD" + PREVIOUS_SEASON);
        Files.delete(previous);
        Files.createDirectory(previous);
        Files.createSymbolicLink(previous.resolve("startEgoGiftPools.json"),
                source.resolve("startEgoGiftPools.json").toAbsolutePath());
        ObjectNode recorded = (ObjectNode) MAPPER.readTree(Files.readString(source.resolve("startBuffs.json")));
        assertThat(recorded.has(buffId)).isTrue();
        recorded.remove(buffId);
        Files.writeString(previous.resolve("startBuffs.json"), recorded.toString());
        return validatorOverDataDir();
    }
}
