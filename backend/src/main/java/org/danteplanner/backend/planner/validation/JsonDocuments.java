package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;

public final class JsonDocuments {

    private static final Comparator<JsonNode> NUMBERS_BY_VALUE = (left, right) -> {
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue());
        }
        return left.equals(right) ? 0 : 1;
    };

    private JsonDocuments() {
    }

    public static boolean sameDocument(JsonNode left, JsonNode right) {
        return left.equals(NUMBERS_BY_VALUE, right);
    }
}
