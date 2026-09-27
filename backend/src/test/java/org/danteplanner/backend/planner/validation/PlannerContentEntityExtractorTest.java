package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor.EntityRef;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerContentEntityExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private static Set<EntityRef> extract(MDCategory category) throws Exception {
        return PlannerContentEntityExtractor.extract(MAPPER.readTree(SIX_FLOORS), category);
    }

    @Test
    void extract_WhenAFiveFloorPlannerStoresASixthFloor_SkipsItsThemePackAndGifts() throws Exception {
        assertThat(extract(MDCategory.F5))
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
        assertThat(extract(MDCategory.F15))
                .contains(
                        new EntityRef(ContentEntityType.THEME_PACK, 1006),
                        new EntityRef(ContentEntityType.EGO_GIFT, 9004));
    }
}
