package org.danteplanner.backend.planner.validation.floor;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.EncodedGiftId;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable.CategoryFloors;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable.RuleName;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
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
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.MAPPER;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.TABLE;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.floor;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.floors;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.json;

class FloorRulesCorpusTest {

    private static final Path CORPUS_FILE = Path.of("../testdata/planner-floor-rules.corpus.json");

    private static final Set<String> MODULE_CODES = codes(
            ErrorCode.INVALID_FIELD_TYPE,
            ErrorCode.INSUFFICIENT_FLOORS,
            ErrorCode.MISSING_THEMEPACK,
            ErrorCode.DUPLICATE_THEMEPACK,
            ErrorCode.OUT_OF_ORDER_THEMEPACK,
            ErrorCode.OUT_OF_ORDER_DIFFICULTY,
            ErrorCode.OUT_OF_RANGE_DIFFICULTY,
            ErrorCode.INSUFFICIENT_DIFFICULTY_ENTRY,
            ErrorCode.OUT_OF_RANGE_DIFFICULTY_NOT_HARD,
            ErrorCode.OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME,
            ErrorCode.DUPLICATE_EGOGIFT);

    private static final Set<String> ID_CHECK_CODES = codes(
            ErrorCode.UNKNOWN_THEMEPACK,
            ErrorCode.UNKNOWN_EGOGIFT_FLOOR,
            ErrorCode.UNAFFORDABLE_EGOGIFT);

    private static final int CASE_COUNT = 62;

    private static final FloorReferenceData CORPUS_GAME_DATA = corpusGameData(readCorpus().path("gameData"));

    private final FloorRules rules = new FloorRules(TABLE);

    static Stream<Arguments> corpusCases() {
        return allCases()
                .flatMap(corpusCase -> Stream.of(Stage.values())
                        .map(stage -> Arguments.of(corpusCase.path("name").asText(), stage, corpusCase)));
    }

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("corpusCases")
    void floorSubject_WhenCorpusCaseRuns_ReportsTheRecordedViolationsInOrder(String name, Stage stage,
                                                                             JsonNode corpusCase) {
        JsonNode expect = corpusCase.path("expect").path(stage.tableName());
        JsonNode floorSelections = corpusCase.path("floorSelections");
        FloorBoundary.Parsed parsed = FloorBoundary.parse(floorSelections,
                TABLE.layout(MDCategory.fromValue(corpusCase.path("category").asText())));
        Admission admission = rules.admit(parsed, stage);
        List<Violation> references = stage == Stage.INDEX ? List.of() : FloorReferences.check(parsed, CORPUS_GAME_DATA);

        assertThat(pairsOf(Stream.concat(violationsOf(admission).stream(), references.stream())
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList()))
                .isEqualTo(pairs(expect.path("violations")));
        if (stage == Stage.INDEX) {
            assertThat(indicesOf(parsed, ParsedFloor.Accepted.class)).isEqualTo(integers(expect.path("floors")));
            assertThat(indicesOf(parsed, ParsedFloor.Rejected.class)).isEqualTo(integers(expect.path("salvaged")));
            assertThat(admission).isInstanceOfSatisfying(Admission.Admitted.class, admitted ->
                    assertThat(FloorFixtures.outcomeFloors(admitted.floors())).isEqualTo(expect.path("admitted")));
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

        assertThat(named).containsAll(recordedCodes);
        assertThat(cases).hasSize(CASE_COUNT);
    }

    @ParameterizedTest(name = "{0} at {1}")
    @CsvSource(delimiter = '|', textBlock = """
            field-type-floor-selections | DRAFT | Field 'floorSelections' must be array, got object
            floor-theme-pack-empty-rejected-on-publish | PUBLISH | floorSelections[4] must have a theme pack selected
            floor-theme-pack-absent-rejected-on-publish | PUBLISH | floorSelections[4] must have a theme pack selected
            floor-difficulty-out-of-range | PUBLISH | floorSelections[0].difficulty value 5 is out of range [0-1]
            floor-difficulty-absent | PUBLISH | floorSelections[0].difficulty is absent; expected a value in range [0-1]
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
        JsonNode floors = floors(floor("1001", 0), json("5"), json("{\"difficulty\":0}"), floor("1001", 0),
                floor("1005", 0));

        for (Stage stage : List.of(Stage.DRAFT, Stage.PUBLISH)) {
            assertThat(admit(rules, floors, MDCategory.F5, stage)).isEqualTo(new Admission.Rejected(List.of(
                    new Violation(ErrorCode.INVALID_FIELD_TYPE, ContentPath.of("floorSelections").index(1),
                            "Field 'floorSelections[1]' must be object, got number 5"))));
        }
    }

    @Test
    void admit_WhenAFloorBelowTheCountIsNotAnObjectAtIndex_AdmitsItEmptyBesideTheViolation() {
        JsonNode floors = floors(floor("1001", 0), json("5"), json("{\"difficulty\":0}"), floor("1001", 0),
                floor("1005", 0));

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.INDEX);

        assertThat(admission).isEqualTo(new Admission.Admitted(
                List.of(selection("1001", 0), new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of()),
                        new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of()),
                        selection("1001", 0), selection("1005", 0)),
                List.of(new Violation(ErrorCode.INVALID_FIELD_TYPE, ContentPath.of("floorSelections").index(1),
                        "Field 'floorSelections[1]' must be object, got number 5"))));
    }

    @Test
    void admit_WhenAnObjectFloorFailsTheBoundaryAtIndex_AdmitsItsSalvagedFieldsInPlace() {
        JsonNode floors = floors(floor("1001", 0), json("{\"themePackId\":\"1004\",\"giftIds\":[\"9002\",9005]}"),
                json("{\"themePackId\":1003,\"difficulty\":\"x\",\"giftIds\":5}"));

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.INDEX);

        assertThat(admission).isInstanceOfSatisfying(Admission.Admitted.class, admitted -> {
            assertThat(admitted.floors()).containsExactly(selection("1001", 0),
                    new FloorSelection(ThemePack.chosen("1004"), Difficulty.unset(), List.of("9002", "9005")),
                    new FloorSelection(ThemePack.chosen("1003"), Difficulty.unset(), List.of()));
            assertThat(admitted.boundaryViolations()).extracting(violation -> violation.path().render()).containsExactly(
                    "floorSelections[1].giftIds[1]", "floorSelections[2].difficulty", "floorSelections[2].giftIds",
                    "floorSelections[2].themePackId");
        });
    }

    @Test
    void admit_WhenAnObjectFloorFailsTheBoundaryAtDraftOrPublish_StillRejectsWithoutSalvage() {
        JsonNode floors = floors(floor("1001", 0), json("{\"themePackId\":\"1004\",\"giftIds\":[\"9002\",9005]}"));

        for (Stage stage : List.of(Stage.DRAFT, Stage.PUBLISH)) {
            assertThat(admit(rules, floors, MDCategory.F5, stage)).isEqualTo(new Admission.Rejected(List.of(
                    new Violation(ErrorCode.INVALID_FIELD_TYPE, ContentPath.of("floorSelections").index(1).field("giftIds").index(1),
                            "Field 'floorSelections[1].giftIds[1]' must be string, got number 9005"))));
        }
    }

    @Test
    void admit_WhenGiftsAtTwoAndTenAreNotStrings_ReportsTwoBeforeTen() {
        JsonNode floors = json("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9001\",\"9002\",9003,"
                + "\"9004\",\"9005\",\"9006\",\"9007\",\"9008\",\"9009\",\"9010\",9011]}]");

        for (Stage stage : List.of(Stage.DRAFT, Stage.PUBLISH)) {
            assertThat(admit(rules, floors, MDCategory.F5, stage)).isInstanceOfSatisfying(Admission.Rejected.class,
                    rejected -> assertThat(rejected.violations()).extracting(violation -> violation.path().render())
                            .containsExactly("floorSelections[0].giftIds[2]", "floorSelections[0].giftIds[10]"));
        }
    }

    @Test
    void admit_WhenOneFloorBreaksSeveralRulesAtPublish_ReportsEveryViolation() {
        JsonNode floors = floors(floor("9999", 1), floor("1002", 0), floor("1002", 0), floor("1004", 1),
                floor("1005", 1));

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.PUBLISH);

        assertThat(admission).isInstanceOfSatisfying(Admission.Rejected.class, rejected ->
                assertThat(rejected.violations()).extracting(Violation::code, violation -> violation.path().render()).containsExactly(
                        tuple(ErrorCode.OUT_OF_ORDER_DIFFICULTY, "floorSelections[1].difficulty"),
                        tuple(ErrorCode.OUT_OF_ORDER_DIFFICULTY, "floorSelections[2].difficulty"),
                        tuple(ErrorCode.DUPLICATE_THEMEPACK, "floorSelections[2].themePackId")));
    }

    @Test
    void admit_WhenAFloorRepeatsSeveralGifts_ReportsOneDuplicateForThatFloor() {
        JsonNode floors = json("[{\"themePackId\":\"1001\",\"difficulty\":0,"
                + "\"giftIds\":[\"9001\",\"9001\",\"9002\",\"9002\",\"9001\"]}]");

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.DRAFT);

        assertThat(admission).isEqualTo(new Admission.Rejected(List.of(new Violation(ErrorCode.DUPLICATE_EGOGIFT,
                ContentPath.of("floorSelections").index(0).field("giftIds"), "Duplicate value '9001' in floorSelections[0].giftIds"))));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            9001  | 19001
            19001 | 9001
            29001 | 19001
            """)
    void admit_WhenAFloorHoldsTwoFormsOfOneGift_ReportsTheLaterAsARepeat(String first, String repeat) {
        JsonNode floors = json("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"" + first + "\",\""
                + repeat + "\"]}]");

        assertThat(admit(rules, floors, MDCategory.F5, Stage.DRAFT)).isEqualTo(new Admission.Rejected(List.of(
                new Violation(ErrorCode.DUPLICATE_EGOGIFT, ContentPath.of("floorSelections").index(0).field("giftIds"),
                        "Duplicate value '" + repeat + "' in floorSelections[0].giftIds"))));
    }

    @Test
    void admit_WhenTwoFloorsHoldTwoFormsOfOneGift_ReportsNoRepeat() {
        JsonNode floors = floors(json("{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9001\"]}"),
                json("{\"themePackId\":\"1002\",\"difficulty\":0,\"giftIds\":[\"19001\"]}"));

        assertThat(admit(rules, floors, MDCategory.F5, Stage.DRAFT)).isInstanceOf(Admission.Admitted.class);
    }

    @Test
    void admit_WhenTheMatrixMovesDifficultyInRangeToDraft_ChecksTheDifficultyAtDraftInstead() {
        JsonNode floors = floors(floor("1001", 5), floor("1002", 0), floor("1003", 0), floor("1004", 0),
                floor("1005", 0));
        Map<RuleName, Set<String>> moved = new HashMap<>(TABLE.rules());
        moved.put(RuleName.DIFFICULTY_IN_RANGE, Set.of("draft"));
        FloorRules movedRules = new FloorRules(
                new FloorRuleTable(TABLE.schemaVersion(), TABLE.stages(), TABLE.categories(), moved));
        Admission.Rejected outOfRange = new Admission.Rejected(List.of(new Violation(ErrorCode.OUT_OF_RANGE_DIFFICULTY,
                ContentPath.of("floorSelections").index(0).field("difficulty"), "floorSelections[0].difficulty value 5 is out of range [0-1]")));

        assertThat(TABLE.stagesFor(RuleName.DIFFICULTY_IN_RANGE)).containsExactly("publish");
        assertThat(admit(rules, floors, MDCategory.F5, Stage.DRAFT)).isInstanceOf(Admission.Admitted.class);
        assertThat(admit(rules, floors, MDCategory.F5, Stage.PUBLISH)).isEqualTo(outOfRange);
        assertThat(admit(movedRules, floors, MDCategory.F5, Stage.DRAFT)).isEqualTo(outOfRange);
        assertThat(admit(movedRules, floors, MDCategory.F5, Stage.PUBLISH)).isInstanceOf(Admission.Admitted.class);
    }

    @Test
    void admit_WhenNormalFollowsHardOnFiveFloors_ReportsEveryLaterNormalFloor() {
        JsonNode floors = floors(floor("1001", 1), floor("1002", 0), floor("1003", 0), floor("1004", 1),
                floor("1005", 1));

        Admission admission = admit(rules, floors, MDCategory.F5, Stage.PUBLISH);

        assertThat(admission).isEqualTo(new Admission.Rejected(List.of(
                new Violation(ErrorCode.OUT_OF_ORDER_DIFFICULTY, ContentPath.of("floorSelections").index(1).field("difficulty"),
                        "Invalid sequence: floorSelections[1].difficulty is NORMAL after HARD in "
                                + "floorSelections[0].difficulty"),
                new Violation(ErrorCode.OUT_OF_ORDER_DIFFICULTY, ContentPath.of("floorSelections").index(2).field("difficulty"),
                        "Invalid sequence: floorSelections[2].difficulty is NORMAL after HARD in "
                                + "floorSelections[0].difficulty"))));
    }

    @Test
    void admit_WhenNormalFollowsHardWhereNormalIsNotAllowed_ReportsTheRangeAlone() {
        JsonNode floors = floors(IntStream.range(0, TABLE.floorCount(MDCategory.F10))
                .mapToObj(index -> floor(String.valueOf(1001 + index), index == 3 ? 0 : 1))
                .toArray(JsonNode[]::new));

        Admission admission = admit(rules, floors, MDCategory.F10, Stage.PUBLISH);

        assertThat(admission).isInstanceOfSatisfying(Admission.Rejected.class, rejected ->
                assertThat(rejected.violations()).extracting(Violation::code, violation -> violation.path().render()).containsExactly(
                        tuple(ErrorCode.INSUFFICIENT_DIFFICULTY_ENTRY, "floorSelections[3].difficulty")));
    }

    @Test
    void admit_WhenTheShortestCategoryShrinks_MovesTheEntryFloorsWithIt() {
        Map<MDCategory, CategoryFloors> categories = new EnumMap<>(TABLE.categories());
        categories.put(MDCategory.F5, new CategoryFloors(3, TABLE.layout(MDCategory.F5).difficulties().subList(0, 3)));
        FloorRules shrunk = new FloorRules(
                new FloorRuleTable(TABLE.schemaVersion(), TABLE.stages(), categories, TABLE.rules()));
        JsonNode floors = floors(IntStream.range(0, TABLE.floorCount(MDCategory.F10))
                .mapToObj(index -> floor(String.valueOf(1001 + index), index == 2 || index == 3 ? 0 : 1))
                .toArray(JsonNode[]::new));

        Admission admission = admit(shrunk, floors, MDCategory.F10, Stage.PUBLISH);

        assertThat(admission).isInstanceOfSatisfying(Admission.Rejected.class, rejected ->
                assertThat(rejected.violations()).extracting(Violation::code, violation -> violation.path().render())
                        .containsExactly(
                                tuple(ErrorCode.INSUFFICIENT_DIFFICULTY_ENTRY, "floorSelections[2].difficulty"),
                                tuple(ErrorCode.OUT_OF_RANGE_DIFFICULTY_NOT_HARD, "floorSelections[3].difficulty")));
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

        assertThatThrownBy(() -> new FloorRules(twoStages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[draft, publish]");
    }

    private static Stream<JsonNode> allCases() {
        return StreamSupport.stream(readCorpus().path("cases").spliterator(), false);
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
        return floorRules.admit(FloorBoundary.parse(floorSelections, TABLE.layout(category)), stage);
    }

    private static List<Integer> indicesOf(FloorBoundary.Parsed parsed, Class<? extends ParsedFloor> kind) {
        return IntStream.range(0, parsed.floors().size())
                .filter(index -> kind.isInstance(parsed.floors().get(index)))
                .boxed()
                .toList();
    }

    private static List<Integer> integers(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asInt).toList();
    }

    private static List<Violation> violationsOf(Admission admission) {
        return switch (admission) {
            case Admission.Admitted admitted -> admitted.boundaryViolations();
            case Admission.Rejected rejected -> rejected.violations();
        };
    }

    private static FloorReferenceData corpusGameData(JsonNode gameData) {
        Set<String> themePackIds = new HashSet<>();
        gameData.path("themePackIds").forEach(id -> themePackIds.add(id.asText()));
        Map<String, List<String>> giftThemePacks = new HashMap<>();
        gameData.path("egoGiftThemePacks").properties().forEach(gift -> giftThemePacks.put(gift.getKey(),
                StreamSupport.stream(gift.getValue().spliterator(), false).map(JsonNode::asText).toList()));
        return new CorpusGameData(Set.copyOf(themePackIds), Map.copyOf(giftThemePacks));
    }

    private record CorpusGameData(Set<String> themePackIds, Map<String, List<String>> giftThemePacks)
            implements FloorReferenceData {

        @Override
        public boolean hasThemePack(String themePackId) {
            return themePackIds.contains(themePackId);
        }

        @Override
        public boolean hasEgoGift(String giftId) {
            return giftThemePacks.containsKey(baseOf(giftId));
        }

        @Override
        public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
            List<String> themePacks = giftThemePacks.get(baseOf(giftId));
            return themePacks.isEmpty() || themePacks.contains(themePackId);
        }

        private static String baseOf(String giftId) {
            return EncodedGiftId.parse(giftId).base().value();
        }
    }

    private static List<List<String>> pairs(JsonNode violations) {
        List<List<String>> pairs = new ArrayList<>();
        violations.forEach(violation -> pairs.add(
                List.of(violation.path("code").asText(), violation.path("path").asText())));
        return pairs;
    }

    private static List<List<String>> pairsOf(List<Violation> violations) {
        return violations.stream().map(violation -> List.of(violation.code().getCode(), violation.path().render())).toList();
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
}
