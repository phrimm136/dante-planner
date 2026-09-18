package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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

    public static boolean isValid(String value) {
        return EnumLookup.isValid(RRCategory.class, value);
    }
}
