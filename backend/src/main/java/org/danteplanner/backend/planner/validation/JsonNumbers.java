package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class JsonNumbers {

    private JsonNumbers() {
    }

    public static Optional<Integer> intValue(JsonNode node) {
        return switch (node.getNodeType()) {
            case NUMBER -> integralInt(node);
            default -> Optional.empty();
        };
    }

    private static Optional<Integer> integralInt(JsonNode number) {
        return switch (number.numberType()) {
            case INT, LONG, BIG_INTEGER -> number.canConvertToInt() ? Optional.of(number.intValue()) : Optional.empty();
            case FLOAT, DOUBLE, BIG_DECIMAL -> Optional.empty();
        };
    }
}
