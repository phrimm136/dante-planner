package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlannerType implements ValuedEnum {
    MIRROR_DUNGEON("MIRROR_DUNGEON") {
        @Override
        public boolean isValidCategory(String category) {
            return MDCategory.isValid(category);
        }
    },
    REFRACTED_RAILWAY("REFRACTED_RAILWAY") {
        @Override
        public boolean isValidCategory(String category) {
            return RRCategory.isValid(category);
        }
    };

    private final String value;

    PlannerType(String value) {
        this.value = value;
    }

    public abstract boolean isValidCategory(String category);

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PlannerType fromValue(String value) {
        return EnumLookup.fromValue(PlannerType.class, value);
    }
}
