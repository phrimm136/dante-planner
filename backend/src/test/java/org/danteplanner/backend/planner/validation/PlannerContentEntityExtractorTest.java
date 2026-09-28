package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.floor.FloorRules;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor.EntityRef;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerContentEntityExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void extract_WhenTheBoundaryRejectsSomeFloors_IndexesTheFloorsThatPassed() throws Exception {
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
                        new EntityRef(ContentEntityType.EGO_GIFT, 9004));
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
