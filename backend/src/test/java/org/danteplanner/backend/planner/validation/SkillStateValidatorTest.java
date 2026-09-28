package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.floor.Stage;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SkillStateValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ValidationContext context = new ValidationContext(Stage.DRAFT);

    private final SkillStateValidator validator = new SkillStateValidator();

    @Test
    void validateSkillEAState_WhenSlotsAreIntegers_RejectsNothing() {
        validator.validateSkillEAState(root(skillEAStateWithSinnerFive("{\"0\":3,\"1\":2,\"2\":1}")), context);

        assertThat(context.getErrors()).isEmpty();
    }

    @Test
    void validateSkillEAState_WhenASlotIsAnIntegralValuedFraction_RejectsItAsANonNumberIs() {
        validator.validateSkillEAState(root(skillEAStateWithSinnerFive("{\"0\":1.0,\"1\":2,\"2\":3}")), context);

        assertThat(context.getErrors())
                .extracting(PlannerValidationException::getOriginalCode, PlannerValidationException::getMessage)
                .contains(tuple("INVALID_FIELD_TYPE", "Field 'skillEAState[05][0]' must be number, got number 1.0"));
    }

    private static String skillEAStateWithSinnerFive(String sinnerFiveSlots) {
        String sinners = SinnerKeys.ALL_SINNER_KEYS.stream()
                .sorted()
                .map(key -> "\"" + key + "\":" + ("05".equals(key) ? sinnerFiveSlots : "{\"0\":3,\"1\":2,\"2\":1}"))
                .collect(Collectors.joining(","));
        return "{\"skillEAState\":{" + sinners + "}}";
    }

    private JsonNode root(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(json, e);
        }
    }
}
