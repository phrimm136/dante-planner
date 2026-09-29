package org.danteplanner.backend.planner.validation;

import java.util.Comparator;

public record Violation(ErrorCode code, ContentPath path, String message, String value) {

    public static final Comparator<Violation> BY_PATH_THEN_CODE =
            Comparator.comparing(Violation::path).thenComparing(violation -> violation.code().getCode());

    public Violation(ErrorCode code, ContentPath path, String message) {
        this(code, path, message, null);
    }

    public static Violation unknownField(ContentPath path) {
        return new Violation(ErrorCode.UNKNOWN_FIELD, path, String.format("Field '%s' is unknown", path.render()));
    }

    public static Violation missingField(ContentPath path) {
        return new Violation(ErrorCode.MISSING_REQUIRED_FIELD, path, String.format("Field '%s' is missing", path.render()));
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
}
