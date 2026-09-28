package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("floor rule table equals MDCategory.floorCount and IdReferenceValidator.FLOOR_RULES; leaves with those constants")
class FloorRuleTableParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path FLOOR_RULES_FILE = Path.of("../static/data", GameDataRegistry.FLOOR_RULES_FILE);
    private static final int MIN_DIFFICULTY = 0;
    private static final int MAX_DIFFICULTY = 3;

    private final FloorRuleTable table = new GameDataLoader(MAPPER).loadFloorRules(FLOOR_RULES_FILE);
    private final IdReferenceValidator validator = new IdReferenceValidator(null, new SinnerIdValidator());

    @ParameterizedTest
    @EnumSource(MDCategory.class)
    void floorCount_WhenReadFromTheTable_EqualsTheCategoryConstant(MDCategory category) {
        assertThat(table.floorCount(category)).isEqualTo(category.floorCount());
    }

    @ParameterizedTest
    @EnumSource(MDCategory.class)
    void allowedDifficulties_WhenReadFromTheTable_EqualsWhatFloorRulesAcceptAtPublish(MDCategory category) {
        List<Set<Integer>> accepted = IntStream.range(0, category.floorCount())
                .mapToObj(floor -> (Set<Integer>) new HashSet<Integer>())
                .toList();
        for (int difficulty = MIN_DIFFICULTY; difficulty <= MAX_DIFFICULTY; difficulty++) {
            List<String> messages = publishViolations(category, difficulty);
            for (int floor = 0; floor < category.floorCount(); floor++) {
                String prefix = "floorSelections[" + floor + "].difficulty ";
                if (messages.stream().noneMatch(message -> message.startsWith(prefix))) {
                    accepted.get(floor).add(difficulty);
                }
            }
        }

        List<Set<Integer>> fromTable = IntStream.range(0, table.floorCount(category))
                .mapToObj(floor -> table.allowedDifficulties(category, floor))
                .toList();
        assertThat(fromTable).isEqualTo(accepted);
    }

    private List<String> publishViolations(MDCategory category, int difficulty) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode floors = root.putArray("floorSelections");
        for (int floor = 0; floor < category.floorCount(); floor++) {
            floors.addObject()
                    .put("themePackId", "pack" + floor)
                    .put("difficulty", difficulty)
                    .putArray("giftIds");
        }
        ValidationContext context = new ValidationContext(ValidationPolicy.PUBLISH);
        validator.validateFloorRules(root, category.getValue(), context);
        return context.getErrors().stream().map(PlannerValidationException::getMessage).toList();
    }
}
