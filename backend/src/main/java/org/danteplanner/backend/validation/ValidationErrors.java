package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.exception.PlannerValidationException;

import java.util.Set;

/**
 * Factory for {@link PlannerValidationException} instances.
 *
 * <p>Centralizes the mapping from a validation failure to its client-facing
 * {@link ErrorCode} and human-readable message. Messages are part of the API
 * contract and must stay byte-identical.
 */
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

    static PlannerValidationException missingRequiredField(Set<String> fields) {
        return new PlannerValidationException(ErrorCode.MISSING_REQUIRED_FIELD.getCode(),
                "Missing required fields: " + fields);
    }

    static PlannerValidationException unknownField(Set<String> fields) {
        return new PlannerValidationException(ErrorCode.UNKNOWN_FIELD.getCode(),
                "Unknown fields: " + fields);
    }

    static PlannerValidationException invalidCategory(String category) {
        return new PlannerValidationException(ErrorCode.INVALID_CATEGORY.getCode(),
                "Invalid category: " + category);
    }

    static PlannerValidationException invalidFieldType(String field, String expectedType) {
        return new PlannerValidationException(ErrorCode.INVALID_FIELD_TYPE.getCode(),
                String.format("Field '%s' has wrong type, expected %s", field, expectedType));
    }

    static PlannerValidationException invalidFieldType(String field, String expectedType, JsonNode actual) {
        String actualDesc;
        if (actual == null || actual.isNull())    actualDesc = "null";
        else if (actual.isTextual())              actualDesc = "string \"" + truncateValue(actual.asText()) + "\"";
        else if (actual.isNumber())               actualDesc = "number " + actual;
        else if (actual.isBoolean())              actualDesc = "boolean " + actual.asBoolean();
        else                                      actualDesc = actual.getNodeType().toString().toLowerCase();
        return new PlannerValidationException(ErrorCode.INVALID_FIELD_TYPE.getCode(),
                String.format("Field '%s' must be %s, got %s", field, expectedType, actualDesc));
    }

    static PlannerValidationException invalidIdReference(String context, String id) {
        return new PlannerValidationException(ErrorCode.INVALID_ID_REFERENCE.getCode(),
                String.format("%s ID '%s' not found or invalid", context, id));
    }

    static PlannerValidationException valueOutOfRange(String field, int value, int min, int max) {
        return new PlannerValidationException(ErrorCode.VALUE_OUT_OF_RANGE.getCode(),
                String.format("%s value %d is out of range [%d-%d]", field, value, min, max));
    }

    static PlannerValidationException duplicateValue(String field, String value) {
        return new PlannerValidationException(ErrorCode.DUPLICATE_VALUE.getCode(),
                String.format("Duplicate value '%s' in %s", value, field));
    }

    static PlannerValidationException invalidSequence(String detail) {
        return new PlannerValidationException(ErrorCode.INVALID_SEQUENCE.getCode(), "Invalid sequence: " + detail);
    }

    static PlannerValidationException giftNotAffordable(String giftId, String themePackId) {
        return new PlannerValidationException(ErrorCode.GIFT_NOT_AFFORDABLE.getCode(),
                String.format("Gift '%s' is not affordable for theme pack '%s'", giftId, themePackId));
    }

    private static String truncateValue(String s) {
        if (s == null) return "null";
        return s.length() <= MAX_VALUE_LOG_LENGTH ? s : s.substring(0, MAX_VALUE_LOG_LENGTH) + "…";
    }
}
