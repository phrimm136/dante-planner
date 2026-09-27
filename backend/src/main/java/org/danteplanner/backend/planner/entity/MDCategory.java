package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MDCategory implements ValuedEnum {
    F5("5F", 5),
    F10("10F", 10),
    F15("15F", 15);

    private final String value;
    private final int floorCount;

    MDCategory(String value, int floorCount) {
        this.value = value;
        this.floorCount = floorCount;
    }

    public int floorCount() {
        return floorCount;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MDCategory fromValue(String value) {
        return EnumLookup.fromValue(MDCategory.class, value);
    }

    public static boolean isValid(String value) {
        return EnumLookup.isValid(MDCategory.class, value);
    }
}
