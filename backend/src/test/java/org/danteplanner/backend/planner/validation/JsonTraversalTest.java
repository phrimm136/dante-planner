package org.danteplanner.backend.planner.validation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.danteplanner.backend.planner.validation.JsonTraversal.arrayField;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachNumber;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachObject;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachObjectProperty;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachUniqueString;

/**
 * The traversal helper's own contract, apart from any validator using it: which elements reach a
 * body, which are rejected with which message, and which are passed over in silence.
 */
class JsonTraversalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ValidationContext context = new ValidationContext(ValidationPolicy.DRAFT);

    private final List<String> visited = new ArrayList<>();

    @Test
    void eachUniqueString_WhenElementsAreTextualAndDistinct_VisitsEachWithItsIndex() {
        eachUniqueString(arrayField(root("{\"ids\":[\"a\",\"b\"]}"), "ids"), "ids", context,
                (value, index) -> visited.add(index + ":" + value));

        assertThat(visited).containsExactly("0:a", "1:b");
        assertThat(context.getErrors()).isEmpty();
    }

    @Test
    void eachUniqueString_WhenElementIsNotTextual_RejectsAtTheIndexedPathAndSkipsIt() {
        eachUniqueString(arrayField(root("{\"ids\":[\"a\",42]}"), "ids"), "ids", context,
                (value, index) -> visited.add(value));

        assertThat(visited).containsExactly("a");
        assertThat(context.getErrors())
                .extracting(PlannerValidationException::getErrorCode, PlannerValidationException::getMessage)
                .containsExactly(tuple("INVALID_FIELD_TYPE", "Field 'ids[1]' must be string, got number 42"));
    }

    @Test
    void eachUniqueString_WhenValueRepeats_RejectsAtTheArrayPathAndSkipsIt() {
        eachUniqueString(arrayField(root("{\"ids\":[\"a\",\"a\"]}"), "ids"), "ids", context,
                (value, index) -> visited.add(index + ":" + value));

        assertThat(visited).containsExactly("0:a");
        assertThat(context.getErrors())
                .extracting(PlannerValidationException::getErrorCode, PlannerValidationException::getMessage)
                .containsExactly(tuple("DUPLICATE_VALUE", "Duplicate value 'a' in ids"));
    }

    @Test
    void eachUniqueString_WhenFieldIsNotAnArray_VisitsNothingAndRejectsNothing() {
        eachUniqueString(arrayField(root("{\"ids\":\"a\"}"), "ids"), "ids", context,
                (value, index) -> visited.add(value));

        assertThat(visited).isEmpty();
        assertThat(context.getErrors()).isEmpty();
    }

    @Test
    void eachNumber_WhenElementIsNotNumeric_RejectsAtTheIndexedPathAndSkipsIt() {
        eachNumber(arrayField(root("{\"order\":[7,\"x\"]}"), "order"), "order", context,
                (value, index) -> visited.add(index + ":" + value));

        assertThat(visited).containsExactly("0:7");
        assertThat(context.getErrors())
                .extracting(PlannerValidationException::getErrorCode, PlannerValidationException::getMessage)
                .containsExactly(tuple("INVALID_FIELD_TYPE", "Field 'order[1]' must be number, got string \"x\""));
    }

    @Test
    void eachNumber_WhenElementRepeats_VisitsItAgain() {
        eachNumber(arrayField(root("{\"order\":[7,7]}"), "order"), "order", context,
                (value, index) -> visited.add(index + ":" + value));

        assertThat(visited).containsExactly("0:7", "1:7");
        assertThat(context.getErrors()).isEmpty();
    }

    @Test
    void eachObject_WhenElementIsNotAnObject_PassesOverItInSilence() {
        eachObject(arrayField(root("{\"floors\":[5,{\"n\":1}]}"), "floors"), 10,
                (element, index) -> visited.add(index + ":" + element));

        assertThat(visited).containsExactly("1:{\"n\":1}");
        assertThat(context.getErrors()).isEmpty();
    }

    @Test
    void eachObject_WhenTheArrayIsLongerThanTheLimit_StopsAtTheLimit() {
        eachObject(arrayField(root("{\"floors\":[{\"n\":0},{\"n\":1},{\"n\":2}]}"), "floors"), 2,
                (element, index) -> visited.add(String.valueOf(index)));

        assertThat(visited).containsExactly("0", "1");
    }

    @Test
    void eachObjectProperty_WhenAValueIsNotAnObject_PassesOverItInSilence() {
        eachObjectProperty(root("{\"egos\":{\"ZAYIN\":{\"id\":\"1\"},\"TETH\":5}}").path("egos"),
                (key, value) -> visited.add(key));

        assertThat(visited).containsExactly("ZAYIN");
        assertThat(context.getErrors()).isEmpty();
    }

    @Test
    void eachObjectProperty_WhenTheContainerIsNotAnObject_VisitsNothing() {
        eachObjectProperty(root("{\"egos\":5}").path("egos"), (key, value) -> visited.add(key));

        assertThat(visited).isEmpty();
    }

    @Test
    void arrayField_WhenTheFieldIsAbsentOrOfAnotherType_AnswersAnEmptyMissingNode() {
        JsonNode owner = root("{\"ids\":{\"a\":1,\"b\":2}}");

        assertThat(arrayField(owner, "ids").isMissingNode()).isTrue();
        assertThat(arrayField(owner, "ids").size()).isZero();
        assertThat(arrayField(owner, "absent").isMissingNode()).isTrue();
    }

    private JsonNode root(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(json, e);
        }
    }
}
