package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MDCategory {
    F5("5F"),
    F10("10F"),
    F15("15F");

    private final String value;

    MDCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MDCategory fromValue(String value) {
        for (MDCategory category : MDCategory.values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown MDCategory value: " + value);
    }
}
