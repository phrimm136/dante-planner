package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the type of planner.
 * Used to distinguish between different game content types.
 */
public enum PlannerType {
    MIRROR_DUNGEON("MIRROR_DUNGEON"),
    REFRACTED_RAILWAY("REFRACTED_RAILWAY");

    private final String value;

    PlannerType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PlannerType fromValue(String value) {
        for (PlannerType type : PlannerType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PlannerType value: " + value);
    }
}
