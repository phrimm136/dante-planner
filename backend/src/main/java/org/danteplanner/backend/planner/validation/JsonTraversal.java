package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class JsonTraversal {

    private JsonTraversal() {
    }

    @FunctionalInterface
    interface StringElement {
        void accept(String value, int index);
    }

    @FunctionalInterface
    interface NumberElement {
        void accept(int value, int index);
    }

    @FunctionalInterface
    interface ObjectElement {
        void accept(JsonNode element, int index);
    }

    @FunctionalInterface
    interface ObjectProperty {
        void accept(String key, JsonNode value);
    }

    static JsonNode arrayField(JsonNode owner, String field) {
        JsonNode node = owner.path(field);
        return node.isArray() ? node : MissingNode.getInstance();
    }

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
