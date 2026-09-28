package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.GameDataLoader;
import org.danteplanner.backend.planner.validation.GameDataRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FloorRulesPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FloorRuleTable TABLE =
            new GameDataLoader(MAPPER).loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));
    private static final FloorRules RULES = rulesOver(TABLE);
    private static final int TRIES = 500;

    @Property(tries = TRIES)
    void admit_WhenItsAdmittedFloorsAreReserialized_AdmitsTheSameFloorsWithNoViolations(
            @ForAll("floorSelections") JsonNode raw, @ForAll("categories") MDCategory category,
            @ForAll("stages") Stage stage) {
        if (!(admit(raw, category, stage) instanceof Admission.Admitted admitted)) {
            return;
        }

        assertThat(admit(reserialize(admitted.floors()), category, stage))
                .isEqualTo(new Admission.Admitted(admitted.floors(), List.of()));
    }

    @Property(tries = TRIES)
    void admit_WhenFloorsAreAppendedPastTheCount_ReturnsTheSameAdmission(
            @ForAll("floorLists") List<JsonNode> raw, @ForAll("fillers") List<JsonNode> filler,
            @ForAll("appended") List<JsonNode> appended, @ForAll("categories") MDCategory category,
            @ForAll("stages") Stage stage) {
        int count = TABLE.floorCount(category);
        List<JsonNode> base = new ArrayList<>(raw);
        base.addAll(filler.subList(0, Math.max(0, count - raw.size())));
        List<JsonNode> withAppended = new ArrayList<>(base);
        withAppended.addAll(appended);
        Admission reference = admit(FloorArbitraries.array(base.subList(0, count)), category, stage);

        assertThat(admit(FloorArbitraries.array(base), category, stage)).isEqualTo(reference);
        assertThat(admit(FloorArbitraries.array(withAppended), category, stage)).isEqualTo(reference);
    }

    @Property(tries = TRIES)
    void admit_WhenTheRawValueIsAnyJson_ReturnsAnAdmission(
            @ForAll("anyRaw") JsonNode raw, @ForAll("categories") MDCategory category,
            @ForAll("stages") Stage stage) {
        assertThat(admit(raw, category, stage)).isNotNull();
    }

    @Provide
    Arbitrary<JsonNode> floorSelections() {
        return FloorArbitraries.rawFloorSelections();
    }

    @Provide
    Arbitrary<List<JsonNode>> floorLists() {
        return FloorArbitraries.rawFloorList(0, FloorArbitraries.MAX_RAW_FLOORS);
    }

    @Provide
    Arbitrary<List<JsonNode>> fillers() {
        return FloorArbitraries.rawFloorList(FloorArbitraries.FILLER_FLOORS, FloorArbitraries.FILLER_FLOORS);
    }

    @Provide
    Arbitrary<List<JsonNode>> appended() {
        return FloorArbitraries.pastCountFloor().list().ofMaxSize(FloorArbitraries.MAX_APPENDED_FLOORS);
    }

    @Provide
    Arbitrary<JsonNode> anyRaw() {
        return FloorArbitraries.anyRaw();
    }

    @Provide
    Arbitrary<MDCategory> categories() {
        return FloorArbitraries.category(TABLE);
    }

    @Provide
    Arbitrary<Stage> stages() {
        return FloorArbitraries.stage();
    }

    private static Admission admit(JsonNode raw, MDCategory category, Stage stage) {
        return RULES.admit(FloorBoundary.parse(raw, TABLE.floorCount(category)), category, stage);
    }

    private static JsonNode reserialize(List<FloorSelection> floors) {
        ArrayNode array = MAPPER.createArrayNode();
        for (FloorSelection floor : floors) {
            ObjectNode node = array.addObject();
            if (floor.themePack() instanceof ThemePack.Chosen(String id)) {
                node.put("themePackId", id);
            }
            if (floor.difficulty() instanceof Difficulty.Set(int value)) {
                node.put("difficulty", value);
            }
            floor.giftIds().forEach(node.putArray("giftIds")::add);
        }
        return array;
    }

    private static FloorRules rulesOver(FloorRuleTable table) {
        GameDataRegistry registry = mock(GameDataRegistry.class);
        when(registry.floorRules()).thenReturn(table);
        return new FloorRules(registry);
    }
}
