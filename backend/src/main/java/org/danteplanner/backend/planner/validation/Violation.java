package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.Locale;

public record Violation(ErrorCode code, ContentPath path, String message) {

    public static final Comparator<Violation> BY_PATH_THEN_CODE =
            Comparator.comparing(Violation::path).thenComparing(violation -> violation.code().getCode());

    private static final int MAX_VALUE_LOG_LENGTH = 100;

    public static Violation invalidFieldType(ContentPath path, JsonKind expected, JsonNode actual) {
        return new Violation(ErrorCode.INVALID_FIELD_TYPE, path,
                String.format("Field '%s' must be %s, got %s", path.render(), expected.rendered(), describe(actual)));
    }

    public static Violation valueOutOfRange(ContentPath path, String value, int min, int max) {
        return new Violation(ErrorCode.VALUE_OUT_OF_RANGE, path,
                String.format("%s value %s is out of range [%d-%d]", path.render(), value, min, max));
    }

    public static Violation duplicateValue(ContentPath path, String value) {
        return new Violation(ErrorCode.DUPLICATE_VALUE, path,
                String.format("Duplicate value '%s' in %s", value, path.render()));
    }

    public static Violation unknownId(ErrorCode code, ContentPath path, String id) {
        return new Violation(code, path, String.format("%s ID '%s' not found or invalid", path.render(), id));
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
