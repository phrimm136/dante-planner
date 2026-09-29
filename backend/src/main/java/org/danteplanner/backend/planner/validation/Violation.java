package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

public record Violation(ErrorCode code, ContentPath path, String message, String value) {

    public static final Comparator<Violation> BY_PATH_THEN_CODE =
            Comparator.comparing(Violation::path).thenComparing(violation -> violation.code().getCode());

    private static final int MAX_VALUE_LOG_LENGTH = 100;

    public Violation(ErrorCode code, ContentPath path, String message) {
        this(code, path, message, null);
    }

    public static Violation invalidFieldType(ContentPath path, JsonKind expected, JsonNode actual) {
        return new Violation(ErrorCode.INVALID_FIELD_TYPE, path,
                String.format("Field '%s' must be %s, got %s", path.render(), expected.rendered(), describe(actual)));
    }

    public static Violation wrongType(ErrorCode code, ContentPath path, String subject, String expected) {
        return new Violation(code, path, String.format("Field '%s' has wrong type, expected %s", subject, expected));
    }

    public static Violation missing(ErrorCode code, ContentPath path) {
        return new Violation(code, path, String.format("Field '%s' is missing", path.render()));
    }

    public static Violation missingFields(ErrorCode code, ContentPath path, Collection<String> fields) {
        return new Violation(code, path,
                String.format("Missing required fields: %s", fields.stream().sorted().toList()));
    }

    public static Violation valueOutOfRange(ErrorCode code, ContentPath path, String value, int min, int max) {
        return valueOutOfRange(code, path, path.render(), value, min, max);
    }

    public static Violation valueOutOfRange(ErrorCode code, ContentPath path, String subject, String value, int min,
                                            int max) {
        return new Violation(code, path,
                String.format("%s value %s is out of range [%d-%d]", subject, value, min, max));
    }

    public static Violation duplicateValue(ErrorCode code, ContentPath path, String value) {
        return duplicateValue(code, path, path.render(), value);
    }

    public static Violation duplicateValue(ErrorCode code, ContentPath path, String subject, String value) {
        return new Violation(code, path, String.format("Duplicate value '%s' in %s", value, subject));
    }

    public static Violation unknownId(ErrorCode code, ContentPath path, String id) {
        return unknownId(code, path, path.render(), id);
    }

    public static Violation unknownId(ErrorCode code, ContentPath path, String subject, String id) {
        return new Violation(code, path, unknownIdMessage(subject, id));
    }

    public static Violation unknownValue(ErrorCode code, ContentPath path, String subject, String value) {
        return new Violation(code, path, unknownIdMessage(subject, value), value);
    }

    private static String unknownIdMessage(String subject, String id) {
        return String.format("%s ID '%s' not found or invalid", subject, id);
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
