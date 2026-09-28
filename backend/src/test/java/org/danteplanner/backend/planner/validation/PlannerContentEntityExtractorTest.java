package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.floor.FloorRules;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor.EntityRef;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerContentEntityExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final JsonNode CORPUS = readCorpus(Path.of("../testdata/planner-floor-rules.corpus.json"));

    private static final FloorRuleTable TABLE = new GameDataLoader(MAPPER)
            .loadFloorRules(Path.of("../static/data", GameDataRegistry.FLOOR_RULES_FILE));

    private static final String SIX_FLOORS = """
            {"floorSelections":[
              {"themePackId":"1001","giftIds":["9001"]},
              {"themePackId":"1002","giftIds":[]},
              {"themePackId":"1003","giftIds":[]},
              {"themePackId":"1004","giftIds":[]},
              {"themePackId":"1005","giftIds":[]},
              {"themePackId":"1006","giftIds":["9004"]}
            ]}
            """;

    private final PlannerContentEntityExtractor extractor = extractorOverTheTable();

    private static PlannerContentEntityExtractor extractorOverTheTable() {
        GameDataRegistry registry = mock(GameDataRegistry.class);
        when(registry.floorRules()).thenReturn(TABLE);
        return new PlannerContentEntityExtractor(new FloorRules(registry), registry);
    }

    private static JsonNode readCorpus(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Set<EntityRef> extract(String content, MDCategory category) throws Exception {
        return extractor.extract(MAPPER.readTree(content), category);
    }

    @Test
    void extract_WhenAFiveFloorPlannerStoresASixthFloor_SkipsItsThemePackAndGifts() throws Exception {
        assertThat(extract(SIX_FLOORS, MDCategory.F5))
                .containsExactlyInAnyOrder(
                        new EntityRef(ContentEntityType.THEME_PACK, 1001),
                        new EntityRef(ContentEntityType.THEME_PACK, 1002),
                        new EntityRef(ContentEntityType.THEME_PACK, 1003),
                        new EntityRef(ContentEntityType.THEME_PACK, 1004),
                        new EntityRef(ContentEntityType.THEME_PACK, 1005),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9001));
    }

    @Test
    void extract_WhenAFifteenFloorPlannerStoresTheSameSixthFloor_IndexesIt() throws Exception {
        assertThat(extract(SIX_FLOORS, MDCategory.F15))
                .contains(
                        new EntityRef(ContentEntityType.THEME_PACK, 1006),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9004));
    }

    @Test
    void extract_WhenTheBoundaryRejectsSomeFloors_IndexesTheFieldsThatParsedAsTheProcedureDoes() throws Exception {
        String content = """
                {"floorSelections":[
                  {"themePackId":"1001","giftIds":["9001"]},
                  7,
                  {"themePackId":1003,"giftIds":["9003"]},
                  {"themePackId":"1004","giftIds":["9002",9005]},
                  {"giftIds":["9004"]}
                ]}
                """;

        assertThat(extract(content, MDCategory.F5))
                .containsExactlyInAnyOrder(
                        new EntityRef(ContentEntityType.THEME_PACK, 1001),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9001),
                        new EntityRef(ContentEntityType.THEME_PACK, 1003),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9003),
                        new EntityRef(ContentEntityType.THEME_PACK, 1004),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9002),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9005),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9004));
    }

    static Stream<Arguments> moduleCorpusCases() {
        return StreamSupport.stream(CORPUS.path("cases").spliterator(), false)
                .filter(corpusCase -> MDCategory.isValid(corpusCase.path("category").asText()))
                .map(corpusCase -> Arguments.of(corpusCase.path("name").asText(), corpusCase));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("moduleCorpusCases")
    void extract_WhenCorpusCaseIsIndexed_YieldsTheRecordedFloorRows(String name, JsonNode corpusCase) {
        ObjectNode content = CORPUS.path("base").deepCopy();
        content.set("floorSelections", corpusCase.path("floorSelections"));

        Set<String> floorRows = extractor.extract(content, MDCategory.fromValue(corpusCase.path("category").asText()))
                .stream()
                .filter(ref -> ref.type() == ContentEntityType.THEME_PACK || ref.type() == ContentEntityType.EGO_GIFT)
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());

        assertThat(floorRows).isEqualTo(textSet(corpusCase.path("expect").path("index").path("rows")));
    }

    static Stream<Arguments> procedureCapturedCases() {
        return moduleCorpusCases()
                .filter(arguments -> ((JsonNode) arguments.get()[1]).path("captured").path("index.sql").isArray());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("procedureCapturedCases")
    void corpusIndexRows_WhenTheProcedureWasCaptured_EqualItsRows(String name, JsonNode corpusCase) {
        assertThat(textSet(corpusCase.path("expect").path("index").path("rows")))
                .isEqualTo(textSet(corpusCase.path("captured").path("index.sql")));
    }

    private static Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    @Test
    void extract_WhenIdsCarryASign_DropsThem() throws Exception {
        String content = """
                {"equipment":{"01":{"identity":{"id":"+10101"},"egos":{"ZAYIN":{"id":"-20101"}}}},
                 "selectedGiftIds":["+9001"," 9002"],
                 "floorSelections":[
                  {"themePackId":"+1001","giftIds":["+9002"]},
                  {"themePackId":"1002","giftIds":["9003"]}
                ]}
                """;

        assertThat(extract(content, MDCategory.F5))
                .containsExactlyInAnyOrder(
                        new EntityRef(ContentEntityType.THEME_PACK, 1002),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9003));
    }

    @Test
    void extract_WhenAFifteenFloorPlannerStoresAShortArray_IndexesTheFloorsThatExist() throws Exception {
        String content = """
                {"floorSelections":[
                  {"themePackId":"1001","giftIds":[]},
                  {"themePackId":"1002","giftIds":["9002"]}
                ]}
                """;

        assertThat(extract(content, MDCategory.F15))
                .containsExactlyInAnyOrder(
                        new EntityRef(ContentEntityType.THEME_PACK, 1001),
                        new EntityRef(ContentEntityType.THEME_PACK, 1002),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9002));
    }
}
