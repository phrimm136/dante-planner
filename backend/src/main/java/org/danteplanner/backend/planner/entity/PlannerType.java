package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the type of planner.
 * Used to distinguish between different game content types.
 */
public enum PlannerType implements ValuedEnum {
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
        return EnumLookup.fromValue(PlannerType.class, value);
    }
}
