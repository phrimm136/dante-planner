package org.danteplanner.backend.planner.validation.floor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.IntDistribution;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.MAPPER;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.TABLE;

class FloorRulesPropertyTest {

    private static final FloorRules RULES = new FloorRules(TABLE);
    private static final int TRIES = 500;

    private record AppendInput(List<JsonNode> raw, List<JsonNode> filler, List<JsonNode> appended,
            MDCategory category, Stage stage) {}

    @Test
    void admit_WhenItsCleanlyAdmittedFloorsAreReserialized_AdmitsTheSameFloorsWithNoViolations() {
        PropertyChecker.customized().withIterationCount(TRIES).silent().forAll(
                FloorGenerators.rawCase(TABLE, FloorGenerators.rawFloorSelections()), input -> {
                    if (!(admit(input.raw(), input.category(), input.stage()) instanceof Admission.Admitted admitted)
                            || !admitted.boundaryViolations().isEmpty()) {
                        return true;
                    }
                    return admit(reserialize(admitted.floors()), input.category(), input.stage())
                            .equals(new Admission.Admitted(admitted.floors(), List.of()));
                });
    }

    @Test
    void admit_WhenWellShapedFloorsAreAppendedPastTheCount_ReturnsTheSameAdmission() {
        Generator<List<JsonNode>> raw = FloorGenerators.rawFloorList(0, FloorGenerators.MAX_RAW_FLOORS);
        Generator<List<JsonNode>> filler =
                FloorGenerators.rawFloorList(FloorGenerators.FILLER_FLOORS, FloorGenerators.FILLER_FLOORS);
        Generator<List<JsonNode>> appended = Generator.listsOf(
                IntDistribution.uniform(0, FloorGenerators.MAX_APPENDED_FLOORS), FloorGenerators.wellShapedFloor());
        Generator<MDCategory> category = FloorGenerators.category(TABLE);
        Generator<Stage> stage = FloorGenerators.stage();
        Generator<AppendInput> inputs = Generator.from(env -> new AppendInput(env.generate(raw),
                env.generate(filler), env.generate(appended), env.generate(category), env.generate(stage)));

        PropertyChecker.customized().withIterationCount(TRIES).silent().forAll(inputs, input -> {
            int count = TABLE.floorCount(input.category());
            List<JsonNode> base = new ArrayList<>(input.raw().subList(0, Math.min(input.raw().size(), count)));
            base.addAll(input.filler().subList(0, count - base.size()));
            List<JsonNode> withAppended = new ArrayList<>(base);
            withAppended.addAll(input.appended());

            return admit(FloorGenerators.array(withAppended), input.category(), input.stage())
                    .equals(admit(FloorGenerators.array(base), input.category(), input.stage()));
        });
    }

    @Test
    void admit_WhenTheRawValueIsAnyJson_ReturnsAnAdmission() {
        PropertyChecker.customized().withIterationCount(TRIES).silent().forAll(
                FloorGenerators.rawCase(TABLE, FloorGenerators.anyRaw()),
                input -> admit(input.raw(), input.category(), input.stage()) != null);
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
