package org.danteplanner.backend.planner.validation.skill;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillPlan(@JsonProperty("0") long first, @JsonProperty("1") long second,
                        @JsonProperty("2") long third) {
}
