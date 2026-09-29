package org.danteplanner.backend.planner.validation.floor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.danteplanner.backend.planner.entity.MDCategory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.MAPPER;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.TABLE;

class FloorRulesPropertyTest {

    private static final FloorRules RULES = new FloorRules(TABLE);
    private static final int TRIES = 500;

    @Property(tries = TRIES)
    void admit_WhenItsCleanlyAdmittedFloorsAreReserialized_AdmitsTheSameFloorsWithNoViolations(
            @ForAll("floorSelections") JsonNode raw, @ForAll("categories") MDCategory category,
            @ForAll("stages") Stage stage) {
        if (!(admit(raw, category, stage) instanceof Admission.Admitted admitted)
                || !admitted.boundaryViolations().isEmpty()) {
            return;
        }

        assertThat(admit(reserialize(admitted.floors()), category, stage))
                .isEqualTo(new Admission.Admitted(admitted.floors(), List.of()));
    }

    @Property(tries = TRIES)
    void admit_WhenWellShapedFloorsAreAppendedPastTheCount_ReturnsTheSameAdmission(
            @ForAll("floorLists") List<JsonNode> raw, @ForAll("fillers") List<JsonNode> filler,
            @ForAll("appended") List<JsonNode> appended, @ForAll("categories") MDCategory category,
            @ForAll("stages") Stage stage) {
        int count = TABLE.floorCount(category);
        List<JsonNode> base = new ArrayList<>(raw.subList(0, Math.min(raw.size(), count)));
        base.addAll(filler.subList(0, count - base.size()));
        List<JsonNode> withAppended = new ArrayList<>(base);
        withAppended.addAll(appended);

        assertThat(admit(FloorArbitraries.array(withAppended), category, stage))
                .isEqualTo(admit(FloorArbitraries.array(base), category, stage));
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
        return FloorArbitraries.wellShapedFloor().list().ofMaxSize(FloorArbitraries.MAX_APPENDED_FLOORS);
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
        return RULES.admit(FloorFixtures.parse(raw, category), stage);
    }

    private static JsonNode reserialize(List<FloorSelection> floors) {
        ArrayNode array = MAPPER.createArrayNode();
        floors.forEach(floor -> array.add(reserialized(floor)));
        return array;
    }

    private static ObjectNode reserialized(FloorSelection floor) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (floor.themePack()) {
            case ThemePack.Chosen(String id) -> node.put("themePackId", id);
            case ThemePack.None() -> node.putNull("themePackId");
        }
        switch (floor.difficulty()) {
            case Difficulty.Set(int value) -> node.put("difficulty", value);
            case Difficulty.OutOfRange(BigInteger value) -> node.put("difficulty", value);
            case Difficulty.Unset() -> {
            }
        }
        ArrayNode giftIds = node.putArray("giftIds");
        floor.giftIds().forEach(giftIds::add);
        return node;
    }
}
