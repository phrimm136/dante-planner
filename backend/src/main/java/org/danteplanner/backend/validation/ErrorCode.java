package org.danteplanner.backend.validation;

/**
 * Client-facing error codes produced by planner content validation.
 *
 * <p>Each constant's wire string is the contract the frontend depends on and
 * must never change without a coordinated client update.
 */
public enum ErrorCode {
    EMPTY_CONTENT("EMPTY_CONTENT"),
    SIZE_EXCEEDED("SIZE_EXCEEDED"),
    MALFORMED_JSON("MALFORMED_JSON"),
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD"),
    UNKNOWN_FIELD("UNKNOWN_FIELD"),
    INVALID_CATEGORY("INVALID_CATEGORY"),
    INVALID_FIELD_TYPE("INVALID_FIELD_TYPE"),
    INVALID_ID_REFERENCE("INVALID_ID_REFERENCE"),
    VALUE_OUT_OF_RANGE("VALUE_OUT_OF_RANGE"),
    DUPLICATE_VALUE("DUPLICATE_VALUE"),
    INVALID_SEQUENCE("INVALID_SEQUENCE"),
    GIFT_NOT_AFFORDABLE("GIFT_NOT_AFFORDABLE");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
