package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MDCategory implements ValuedEnum {
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
        return EnumLookup.fromValue(MDCategory.class, value);
    }

    /**
     * Check if a string value is a valid MD category.
     *
     * @param value the category value to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        return EnumLookup.isValid(MDCategory.class, value);
    }
}
