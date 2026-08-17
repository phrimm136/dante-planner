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

    /**
     * Whether a category string belongs to this planner type's category set.
     *
     * @param category the category value to check
     * @return true if the category is valid for this type
     */
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
