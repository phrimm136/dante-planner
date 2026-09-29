package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.exception.PlannerValidationException;

import java.util.Set;
import java.util.Locale;

final class ValidationErrors {

    private static final int MAX_VALUE_LOG_LENGTH = 100;

    private ValidationErrors() {
    }

    static PlannerValidationException emptyContent() {
        return new PlannerValidationException(ErrorCode.EMPTY_CONTENT.getCode(), "Content is empty or null");
    }

    static PlannerValidationException sizeExceeded(String context, int actual, int limit) {
        return new PlannerValidationException(ErrorCode.SIZE_EXCEEDED.getCode(),
                String.format("%s size %d exceeds limit %d", context, actual, limit));
    }

    static PlannerValidationException malformedJson(String details) {
        return new PlannerValidationException(ErrorCode.MALFORMED_JSON.getCode(),
                "Malformed JSON: " + details);
    }

    static PlannerValidationException missing(ErrorCode code, Set<String> fields) {
        return new PlannerValidationException(code.getCode(),
                "Missing required fields: " + fields);
    }

    static PlannerValidationException unknownField(Set<String> fields) {
        return new PlannerValidationException(ErrorCode.UNKNOWN_FIELD.getCode(),
                "Unknown fields: " + fields);
    }

    static PlannerValidationException unknownKeyword(Set<String> unknown) {
        return new PlannerValidationException(ErrorCode.UNKNOWN_KEYWORD.getCode(),
                "Unknown keywords: " + unknown.stream().sorted().toList());
    }

    static PlannerValidationException invalidCategory(String category) {
        return new PlannerValidationException(ErrorCode.INVALID_CATEGORY.getCode(),
                "Invalid category: " + category);
    }

    static PlannerValidationException invalidFieldType(String field, String expectedType) {
        return invalidField(ErrorCode.INVALID_FIELD_TYPE, field, expectedType);
    }

    static PlannerValidationException invalidField(ErrorCode code, String field, String expectedType) {
        return new PlannerValidationException(code.getCode(),
                String.format("Field '%s' has wrong type, expected %s", field, expectedType));
    }

    static PlannerValidationException invalidFieldType(String field, String expectedType, JsonNode actual) {
        return new PlannerValidationException(ErrorCode.INVALID_FIELD_TYPE.getCode(),
                String.format("Field '%s' must be %s, got %s", field, expectedType, describe(actual)));
    }

    private static String describe(JsonNode actual) {
        if (actual == null) {
            return "null";
        }
        return switch (actual.getNodeType()) {
            case NULL -> "null";
            case STRING -> "string \"" + truncateValue(actual.asText()) + "\"";
            case NUMBER -> "number " + actual;
            case BOOLEAN -> "boolean " + actual.asBoolean();
            default -> actual.getNodeType().toString().toLowerCase(Locale.ROOT);
        };
    }

    static PlannerValidationException unknownId(ErrorCode code, String context, String id) {
        return new PlannerValidationException(code.getCode(),
                String.format("%s ID '%s' not found or invalid", context, id));
    }

    static PlannerValidationException valueOutOfRange(ErrorCode code, String field, int value, int min, int max) {
        return new PlannerValidationException(code.getCode(),
                String.format("%s value %d is out of range [%d-%d]", field, value, min, max));
    }

    static PlannerValidationException duplicateValue(ErrorCode code, String field, String value) {
        return new PlannerValidationException(code.getCode(),
                String.format("Duplicate value '%s' in %s", value, field));
    }

    static PlannerValidationException missingStartGiftKeyword(String field) {
        return new PlannerValidationException(ErrorCode.MISSING_STARTGIFT_KEYWORD.getCode(),
                "Invalid sequence: " + field + " requires selectedGiftKeyword");
    }

    private static String truncateValue(String s) {
        if (s == null) return "null";
        return s.length() <= MAX_VALUE_LOG_LENGTH ? s : s.substring(0, MAX_VALUE_LOG_LENGTH) + "…";
    }
}
