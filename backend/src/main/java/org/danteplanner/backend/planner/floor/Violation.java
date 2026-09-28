package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ErrorCode;

import java.util.Comparator;
import java.util.Locale;

public record Violation(ErrorCode code, String path, String message) {

    static final Comparator<Violation> BY_PATH_THEN_CODE =
            Comparator.comparing(Violation::path).thenComparing(violation -> violation.code().getCode());

    private static final int MAX_VALUE_LOG_LENGTH = 100;

    static Violation invalidFieldType(String path, String expectedType, JsonNode actual) {
        return new Violation(ErrorCode.INVALID_FIELD_TYPE, path,
                String.format("Field '%s' must be %s, got %s", path, expectedType, describe(actual)));
    }

    static Violation floorMissingThemePack(String path) {
        return new Violation(ErrorCode.FLOOR_MISSING_THEME_PACK, path,
                String.format("%s must have a theme pack selected", path));
    }

    static Violation floorDuplicateThemePack(String path, String themePackId, int firstFloorIndex) {
        return new Violation(ErrorCode.FLOOR_DUPLICATE_THEME_PACK, path,
                String.format("%s repeats theme pack '%s' from floorSelections[%d]", path, themePackId, firstFloorIndex));
    }

    static Violation valueOutOfRange(String path, int value, int min, int max) {
        return new Violation(ErrorCode.VALUE_OUT_OF_RANGE, path,
                String.format("%s value %d is out of range [%d-%d]", path, value, min, max));
    }

    static Violation duplicateValue(String path, String value) {
        return new Violation(ErrorCode.DUPLICATE_VALUE, path,
                String.format("Duplicate value '%s' in %s", value, path));
    }

    static Violation invalidSequence(String path, String detail) {
        return new Violation(ErrorCode.INVALID_SEQUENCE, path, "Invalid sequence: " + detail);
    }

    private static String describe(JsonNode actual) {
        return switch (actual.getNodeType()) {
            case NULL -> "null";
            case STRING -> "string \"" + truncate(actual.asText()) + "\"";
            case NUMBER -> "number " + actual;
            case BOOLEAN -> "boolean " + actual.asBoolean();
            default -> actual.getNodeType().toString().toLowerCase(Locale.ROOT);
        };
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LOG_LENGTH ? value : value.substring(0, MAX_VALUE_LOG_LENGTH) + "…";
    }
}
