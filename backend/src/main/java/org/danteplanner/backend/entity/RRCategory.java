package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Refracted Railway category enum.
 * Placeholder for future RR-specific categories.
 */
public enum RRCategory implements ValuedEnum {
    RR_PLACEHOLDER("RR_PLACEHOLDER");

    private final String value;

    RRCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RRCategory fromValue(String value) {
        return EnumLookup.fromValue(RRCategory.class, value);
    }

    /**
     * Check if a string value is a valid RR category.
     *
     * @param value the category value to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        return EnumLookup.isValid(RRCategory.class, value);
    }
}
