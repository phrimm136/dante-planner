package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Refracted Railway category enum.
 * Placeholder for future RR-specific categories.
 */
public enum RRCategory {
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
        for (RRCategory category : RRCategory.values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown RRCategory value: " + value);
    }

    /**
     * Check if a string value is a valid RR category.
     *
     * @param value the category value to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        for (RRCategory category : RRCategory.values()) {
            if (category.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
