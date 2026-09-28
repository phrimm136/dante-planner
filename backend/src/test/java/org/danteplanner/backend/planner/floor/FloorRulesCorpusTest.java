package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.GameDataLoader;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FloorRulesCorpusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CORPUS_FILE = Path.of("../testdata/planner-floor-rules.corpus.json");
    private static final Path FLOOR_RULES_FILE = Path.of("../static/data/plannerFloorRules.json");
    private static final FloorRuleTable TABLE = new GameDataLoader(MAPPER).loadFloorRules(FLOOR_RULES_FILE);

    private static final Set<String> MODULE_CODES = codes(
            ErrorCode.INVALID_FIELD_TYPE,
            ErrorCode.FLOOR_MISSING_THEME_PACK,
            ErrorCode.FLOOR_DUPLICATE_THEME_PACK,
            ErrorCode.INVALID_SEQUENCE,
            ErrorCode.VALUE_OUT_OF_RANGE,
            ErrorCode.DUPLICATE_VALUE);

    private static final Set<String> ID_CHECK_CODES = codes(
            ErrorCode.THEME_PACK_UNKNOWN_ID,
            ErrorCode.FLOOR_UNKNOWN_GIFT_ID,
            ErrorCode.GIFT_NOT_AFFORDABLE);

    private static final Set<String> CALLER_CODES = codes(ErrorCode.INVALID_CATEGORY);

    private static final int MODULE_CASE_COUNT = 53;

    private final FloorRules rules = rulesOver(TABLE);

    static Stream<Arguments> corpusCases() {
        return moduleCases()
                .flatMap(corpusCase -> Stream.of(Stage.values())
                        .map(stage -> Arguments.of(corpusCase.path("name").asText(), stage, corpusCase)));
    }

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("corpusCases")
    void admit_WhenCorpusCaseRuns_ReturnsTheRecordedExpectation(String name, Stage stage, JsonNode corpusCase) {
        JsonNode expect = corpusCase.path("expect").path(stage.tableName());
        List<List<String>> expected = pairs(expect.path("violations")).stream()
                .filter(pair -> MODULE_CODES.contains(pair.get(0)))
                .toList();
        JsonNode floorSelections = corpusCase.path("floorSelections");
        Admission admission = admit(rules, floorSelections,
                MDCategory.fromValue(corpusCase.path("category").asText()), stage);

        switch (admission) {
            case Admission.Admitted admitted -> {
                assertThat(pairsOf(admitted.boundaryViolations())).isEqualTo(expected);
                if (stage == Stage.INDEX) {
                    assertThat(admitted.floors()).isEqualTo(recordedFloors(floorSelections, expect.path("floors")));
                } else {
                    assertThat(expected).isEmpty();
                }
            }
            case Admission.Rejected rejected -> {
                assertThat(stage).isNotEqualTo(Stage.INDEX);
                assertThat(pairsOf(rejected.violations())).isNotEmpty().isEqualTo(expected);
            }
        }
    }

    @Test
    void corpus_WhenItsRecordedCodesAreClassified_FallsIntoTheNamedSets() {
        List<JsonNode> cases = allCases().toList();
        Set<String> recordedCodes = new HashSet<>();
        cases.forEach(corpusCase -> Stream.of(Stage.values()).forEach(stage -> pairs(
                corpusCase.path("expect").path(stage.tableName()).path("violations"))
                .forEach(pair -> recordedCodes.add(pair.get(0)))));
        Set<String> named = new HashSet<>(MODULE_CODES);
        named.addAll(ID_CHECK_CODES);
        named.addAll(CALLER_CODES);
        List<String> callerSideCases = cases.stream()
                .filter(corpusCase -> !MDCategory.isValid(corpusCase.path("category").asText()))
                .map(corpusCase -> corpusCase.path("name").asText())
                .toList();

        assertThat(named).containsAll(recordedCodes);
        assertThat(callerSideCases).containsExactly("boundary-unknown-category");
        assertThat(moduleCases()).hasSize(MODULE_CASE_COUNT);
    }

    @Test
    void corpus_WhenTheCategoryIsUnknown_RecordsOnlyTheCallersCategoryViolation() {
        JsonNode corpusCase = namedCase("boundary-unknown-category");

        for (Stage stage : Stage.values()) {
            assertThat(pairs(corpusCase.path("expect").path(stage.tableName()).path("violations")))
                    .containsExactly(List.of(ErrorCode.INVALID_CATEGORY.getCode(), "category"));
        }
    }

    @ParameterizedTest(name = "{0} at {1}")
    @CsvSource(delimiter = '|', textBlock = """
            field-type-floor-selections | DRAFT | Field 'floorSelections' must be array, got object
            floor-theme-pack-empty-rejected-on-publish | PUBLISH | floorSelections[4] must have a theme pack selected
            floor-theme-pack-absent-rejected-on-publish | PUBLISH | floorSelections[4] must have a theme pack selected
            floor-difficulty-out-of-range | PUBLISH | floorSelections[0].difficulty value 5 is out of range [0-1]
            floor-difficulty-absent | PUBLISH | floorSelections[0].difficulty value -1 is out of range [0-1]
            floor-difficulty-hard-required-on-10f | PUBLISH | floorSelections[0].difficulty value 0 is out of range [1-1]
            floor-difficulty-extreme-required-on-15f | PUBLISH | floorSelections[10].difficulty value 1 is out of range [3-3]
            floor-sequence-gap | DRAFT | Invalid sequence: floorSelections[1] requires themePackId in floorSelections[0]
            floor-gift-duplicate | DRAFT | Duplicate value '9002' in floorSelections[0].giftIds
            floor-gift-not-string | DRAFT | Field 'floorSelections[0].giftIds[0]' must be string, got number 9002
            """)
    void message_WhenGoldenCorpusFloorCaseRuns_MatchesTheRecordedText(String name, Stage stage, String message) {
        JsonNode corpusCase = namedCase(name);

        Admission admission = admit(rules, corpusCase.path("floorSelections"),
                MDCategory.fromValue(corpusCase.path("category").asText()), stage);

        assertThat(admission).isInstanceOfSatisfying(Admission.Rejected.class, rejected ->
                assertThat(rejected.violations()).extracting(Violation::message).contains(message));
    }

    @Test
    void admit_WhenAFloorBelowTheCountIsNotAnObjectAtDraftOrPublish_RejectsWithTheBoundaryViolationAlone() {
        JsonNode floors = json("[" + floor("1001", 0) + ",5,{\"difficulty\":0}," + floor("1001", 0) + ","
                + floor("1005", 0) + "]");

        for (Stage stage : List.of(Stage.DRAFT, Stage.PUBLISH)) {
            assertThat(admit(rules, floors, MDCategory.F5, stage)).isEqualTo(new Admission.Rejected(List.of(
                    new Violation(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[1]",
                            "Field 'floorSelections[1]' must be object, got number 5"))));
        }
    }

    @Test
    void admit_WhenAFloorBelowTheCountIsNotAnObjectAtIndex_AdmitsTheOtherFloorsBesideTheViolation() {
        JsonNode floors = json("[" + floor("1001", 0) + ",5,{\"difficulty\":0}," + floor("1001", 0) + ","
                + floor("1005", 0) + "]");

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.INDEX);

        assertThat(admission).isEqualTo(new Admission.Admitted(
                List.of(selection("1001", 0), new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of()),
                        selection("1001", 0), selection("1005", 0)),
                List.of(new Violation(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[1]",
                        "Field 'floorSelections[1]' must be object, got number 5"))));
    }

    @Test
    void admit_WhenOneFloorBreaksSeveralRulesAtPublish_ReportsEveryViolation() {
        JsonNode floors = json("[" + floor("9999", 1) + "," + floor("1002", 0) + "," + floor("1002", 0) + ","
                + floor("1004", 1) + "," + floor("1005", 1) + "]");

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.PUBLISH);

        assertThat(admission).isInstanceOfSatisfying(Admission.Rejected.class, rejected ->
                assertThat(rejected.violations()).extracting(Violation::code, Violation::path).containsExactly(
                        tuple(ErrorCode.INVALID_SEQUENCE, "floorSelections[1].difficulty"),
                        tuple(ErrorCode.INVALID_SEQUENCE, "floorSelections[2].difficulty"),
                        tuple(ErrorCode.FLOOR_DUPLICATE_THEME_PACK, "floorSelections[2].themePackId")));
    }

    @Test
    void admit_WhenAFloorRepeatsSeveralGifts_ReportsOneDuplicateForThatFloor() {
        JsonNode floors = json("[{\"themePackId\":\"1001\",\"difficulty\":0,"
                + "\"giftIds\":[\"9001\",\"9001\",\"9002\",\"9002\",\"9001\"]}]");

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.DRAFT);

        assertThat(admission).isEqualTo(new Admission.Rejected(List.of(new Violation(ErrorCode.DUPLICATE_VALUE,
                "floorSelections[0].giftIds", "Duplicate value '9001' in floorSelections[0].giftIds"))));
    }

    @Test
    void admit_WhenTheMatrixMovesDifficultyInRangeToDraft_ChecksTheDifficultyAtDraftInstead() {
        JsonNode floors = json("[" + floor("1001", 5) + "," + floor("1002", 0) + "," + floor("1003", 0) + ","
                + floor("1004", 0) + "," + floor("1005", 0) + "]");
        Map<String, Set<String>> moved = new HashMap<>(TABLE.rules());
        moved.put("difficultyInRange", Set.of("draft"));
        FloorRules movedRules = rulesOver(
                new FloorRuleTable(TABLE.schemaVersion(), TABLE.stages(), TABLE.categories(), moved));
        Admission.Rejected outOfRange = new Admission.Rejected(List.of(new Violation(ErrorCode.VALUE_OUT_OF_RANGE,
                "floorSelections[0].difficulty", "floorSelections[0].difficulty value 5 is out of range [0-1]")));

        assertThat(TABLE.stagesFor("difficultyInRange")).containsExactly("publish");
        assertThat(admit(rules, floors, MDCategory.F5, Stage.DRAFT)).isInstanceOf(Admission.Admitted.class);
        assertThat(admit(rules, floors, MDCategory.F5, Stage.PUBLISH)).isEqualTo(outOfRange);
        assertThat(admit(movedRules, floors, MDCategory.F5, Stage.DRAFT)).isEqualTo(outOfRange);
        assertThat(admit(movedRules, floors, MDCategory.F5, Stage.PUBLISH)).isInstanceOf(Admission.Admitted.class);
    }

    @Test
    void admit_WhenNormalFollowsHardOnFiveFloors_ReportsEveryLaterNormalFloor() {
        JsonNode floors = json("[" + floor("1001", 1) + "," + floor("1002", 0) + "," + floor("1003", 0) + ","
                + floor("1004", 1) + "," + floor("1005", 1) + "]");

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.PUBLISH);

        assertThat(admission).isEqualTo(new Admission.Rejected(List.of(
                new Violation(ErrorCode.INVALID_SEQUENCE, "floorSelections[1].difficulty",
                        "Invalid sequence: floorSelections[1].difficulty is NORMAL after HARD in "
                                + "floorSelections[0].difficulty"),
                new Violation(ErrorCode.INVALID_SEQUENCE, "floorSelections[2].difficulty",
                        "Invalid sequence: floorSelections[2].difficulty is NORMAL after HARD in "
                                + "floorSelections[0].difficulty"))));
    }

    @Test
    void admit_WhenNormalFollowsHardWhereNormalIsNotAllowed_ReportsTheRangeAlone() {
        JsonNode floors = json("[" + IntStream.range(0, MDCategory.F10.floorCount())
                .mapToObj(index -> floor(String.valueOf(1001 + index), index == 3 ? 0 : 1))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]");

        Admission admission = admit(rules, floors, MDCategory.F10, Stage.PUBLISH);

        assertThat(admission).isInstanceOfSatisfying(Admission.Rejected.class, rejected ->
                assertThat(rejected.violations()).extracting(Violation::code, Violation::path).containsExactly(
                        tuple(ErrorCode.VALUE_OUT_OF_RANGE, "floorSelections[3].difficulty")));
    }

    @Test
    void admit_WhenParsedWithAnotherFloorCount_IsRefusedAsAProgrammingError() {
        FloorBoundary.Parsed parsedForTen = FloorBoundary.parse(json("[" + floor("1001", 0) + "]"),
                TABLE.floorCount(MDCategory.F10));

        assertThatThrownBy(() -> rules.admit(parsedForTen, MDCategory.F5, Stage.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5F");
    }

    @Test
    void stages_WhenReadFromTheTable_MapOntoEveryStage() {
        assertThat(TABLE.stages().stream().map(Stage::fromTableName)).containsExactlyInAnyOrder(Stage.values());
        assertThatThrownBy(() -> Stage.fromTableName("import")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void floorRules_WhenTheTableStagesDifferFromTheStageAxis_FailToConstruct() {
        FloorRuleTable twoStages = new FloorRuleTable(TABLE.schemaVersion(), List.of("draft", "publish"),
                TABLE.categories(), TABLE.rules());

        assertThatThrownBy(() -> rulesOver(twoStages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[draft, publish]");
    }

    private static Stream<JsonNode> allCases() {
        return StreamSupport.stream(readCorpus().path("cases").spliterator(), false);
    }

    private static Stream<JsonNode> moduleCases() {
        return allCases().filter(corpusCase -> MDCategory.isValid(corpusCase.path("category").asText()));
    }

    private static JsonNode namedCase(String name) {
        return allCases()
                .filter(candidate -> candidate.path("name").asText().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> codes(ErrorCode... codes) {
        return Stream.of(codes).map(ErrorCode::getCode).collect(Collectors.toUnmodifiableSet());
    }

    private static Admission admit(FloorRules floorRules, JsonNode floorSelections, MDCategory category,
                                   Stage stage) {
        return floorRules.admit(FloorBoundary.parse(floorSelections, TABLE.floorCount(category)), category, stage);
    }

    private static FloorRules rulesOver(FloorRuleTable table) {
        GameDataRegistry registry = mock(GameDataRegistry.class);
        when(registry.floorRules()).thenReturn(table);
        return new FloorRules(registry);
    }

    private static List<FloorSelection> recordedFloors(JsonNode floorSelections, JsonNode indices) {
        List<FloorSelection> floors = new ArrayList<>();
        for (JsonNode index : indices) {
            floors.add(recordedFloor(floorSelections.get(index.asInt())));
        }
        return floors;
    }

    private static FloorSelection recordedFloor(JsonNode floor) {
        JsonNode themePackId = floor.path("themePackId");
        JsonNode difficulty = floor.path("difficulty");
        List<String> giftIds = new ArrayList<>();
        floor.path("giftIds").forEach(giftId -> giftIds.add(giftId.textValue()));
        assertThat(giftIds).as("an admitted corpus floor holds string gift ids").doesNotContainNull();
        return new FloorSelection(
                themePackId.isTextual() && !themePackId.textValue().isEmpty()
                        ? ThemePack.chosen(themePackId.textValue())
                        : ThemePack.none(),
                difficulty.isInt() ? Difficulty.of(difficulty.intValue()) : Difficulty.unset(),
                giftIds);
    }

    private static List<List<String>> pairs(JsonNode violations) {
        List<List<String>> pairs = new ArrayList<>();
        violations.forEach(violation -> pairs.add(
                List.of(violation.path("code").asText(), violation.path("path").asText())));
        return pairs;
    }

    private static List<List<String>> pairsOf(List<Violation> violations) {
        return violations.stream().map(violation -> List.of(violation.code().getCode(), violation.path())).toList();
    }

    private static String floor(String themePackId, int difficulty) {
        return "{\"themePackId\":\"" + themePackId + "\",\"difficulty\":" + difficulty + ",\"giftIds\":[]}";
    }

    private static FloorSelection selection(String themePackId, int difficulty) {
        return new FloorSelection(ThemePack.chosen(themePackId), Difficulty.of(difficulty), List.of());
    }

    private static JsonNode readCorpus() {
        try {
            return MAPPER.readTree(CORPUS_FILE.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(text, e);
        }
    }
}
