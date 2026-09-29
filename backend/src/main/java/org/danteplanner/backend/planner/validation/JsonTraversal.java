package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
    interface ObjectProperty {
        void accept(String key, JsonNode value);
    }

    static JsonNode arrayField(JsonNode owner, String field) {
        JsonNode node = owner.path(field);
        return node.isArray() ? node : MissingNode.getInstance();
    }

    static boolean isInt(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt();
    }

    static boolean isAbsentOrBlank(JsonNode node) {
        return switch (node.getNodeType()) {
            case MISSING, NULL -> true;
            case STRING -> node.asText().isBlank();
            default -> false;
        };
    }

    static void eachUniqueString(JsonNode array, String path, ErrorCode duplicateCode, ValidationContext context,
                                 StringElement body) {
        eachString(array, path, context,
                value -> context.reject(path, p -> ValidationErrors.duplicateValue(duplicateCode, p, value)), body);
    }

    static void eachDistinctString(JsonNode array, String path, ValidationContext context, StringElement body) {
        eachString(array, path, context, value -> { }, body);
    }

    private static void eachString(JsonNode array, String path, ValidationContext context, Consumer<String> onRepeat,
                                   StringElement body) {
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
                onRepeat.accept(value);
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

            if (!isInt(element)) {
                context.reject(path + "[" + index + "]",
                        p -> ValidationErrors.invalidFieldType(p, "number", element));
                continue;
            }

            body.accept(element.asInt(), index);
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
