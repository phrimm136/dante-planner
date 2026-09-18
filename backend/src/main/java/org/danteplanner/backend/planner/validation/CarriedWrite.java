package org.danteplanner.backend.planner.validation;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.PlannerStatus;

import java.util.Set;
import java.util.UUID;

@Builder
public record CarriedWrite(
    String title,
    PlannerStatus status,
    String category,
    String content,
    Integer gameContentVersion,
    Integer contentSchemaVersion,
    Set<String> selectedKeywords,
    UUID deviceId
) {
}
