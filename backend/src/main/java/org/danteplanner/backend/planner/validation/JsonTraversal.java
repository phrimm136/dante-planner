package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks the dynamic shapes planner content arrives in, and owns every branch on what a node turned
 * out to be.
 *
 * <p>An iterator hands a body only the elements of the shape it asks for, together with the index
 * or key they sit at, and rejects the rest with the contractual message for that defect. A
 * validator built on these describes what an element must mean, never what type it is.
 *
 * <p>Where an iterator passes over an element in silence, the silence is contractual: the defect
 * either belongs to another validator's field-type pass or is one the document may carry. One
 * defect yields one error, so an iterator adding an error of its own to those cases would double
 * a client's report.
 */
final class JsonTraversal {

    private JsonTraversal() {
    }

    /** Validates one textual element of an array, by its value and the index it sits at. */
    @FunctionalInterface
    interface StringElement {
        void accept(String value, int index);
    }

    /** Validates one numeric element of an array, by its value and the index it sits at. */
    @FunctionalInterface
    interface NumberElement {
        void accept(int value, int index);
    }

    /** Validates one object element of an array, by the object and the index it sits at. */
    @FunctionalInterface
    interface ObjectElement {
        void accept(JsonNode element, int index);
    }

    /** Validates one object-valued property, by its key and the object it holds. */
    @FunctionalInterface
    interface ObjectProperty {
        void accept(String key, JsonNode value);
    }

    /**
     * Reads a field expected to hold an array.
     *
     * @param owner the object carrying the field
     * @param field the field name
     * @return the array, or a missing node of size zero when the field is absent or another type
     */
    static JsonNode arrayField(JsonNode owner, String field) {
        JsonNode node = owner.path(field);
        return node.isArray() ? node : MissingNode.getInstance();
    }

    /**
     * Runs {@code body} over the distinct textual elements of {@code array}, in document order.
     *
     * <p>A non-textual element is rejected at its own indexed path, a repeat of an earlier value at
     * the array's path; neither reaches the body.
     *
     * @param array   the array to walk, as {@link #arrayField} answers it
     * @param path    how errors address the array
     * @param context collects the failures
     * @param body    what the element has to mean
     */
    static void eachUniqueString(JsonNode array, String path, ValidationContext context, StringElement body) {
        if (!array.isArray()) {
            return;
        }

        Set<String> seen = new HashSet<>();

        for (int index = 0; index < array.size(); index++) {
            JsonNode element = array.get(index);

            if (!element.isTextual()) {
                context.reject(path + "[" + index + "]",
                        p -> ValidationErrors.invalidFieldType(p, "string", element));
                continue;
            }

            String value = element.asText();

            if (!seen.add(value)) {
                context.reject(path, p -> ValidationErrors.duplicateValue(p, value));
                continue;
            }

            body.accept(value, index);
        }
    }

    /**
     * Runs {@code body} over the numeric elements of {@code array}, in document order. A
     * non-numeric element is rejected at its own indexed path and does not reach the body.
     *
     * @param array   the array to walk, as {@link #arrayField} answers it
     * @param path    how errors address the array
     * @param context collects the failures
     * @param body    what the element has to mean
     */
    static void eachNumber(JsonNode array, String path, ValidationContext context, NumberElement body) {
        if (!array.isArray()) {
            return;
        }

        for (int index = 0; index < array.size(); index++) {
            JsonNode element = array.get(index);

            if (!element.isNumber()) {
                context.reject(path + "[" + index + "]",
                        p -> ValidationErrors.invalidFieldType(p, "number", element));
                continue;
            }

            body.accept(element.asInt(), index);
        }
    }

    /**
     * Runs {@code body} over the leading object elements of {@code array}. An element of another
     * type is passed over in silence.
     *
     * @param array the array to walk, as {@link #arrayField} answers it
     * @param atMost how many leading elements are looked at at all
     * @param body  what the element has to mean
     */
    static void eachObject(JsonNode array, int atMost, ObjectElement body) {
        if (!array.isArray()) {
            return;
        }

        for (int index = 0; index < array.size() && index < atMost; index++) {
            JsonNode element = array.get(index);
            if (!element.isObject()) {
                continue;
            }

            body.accept(element, index);
        }
    }

    /**
     * Runs {@code body} over the object-valued properties of {@code object}, in document order. A
     * property holding another type, and a container that is not an object at all, are passed over
     * in silence.
     *
     * @param object the object whose properties to walk
     * @param body   what one property has to mean
     */
    static void eachObjectProperty(JsonNode object, ObjectProperty body) {
        if (!object.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> property : object.properties()) {
            JsonNode value = property.getValue();
            if (!value.isObject()) {
                continue;
            }

            body.accept(property.getKey(), value);
        }
    }
}
