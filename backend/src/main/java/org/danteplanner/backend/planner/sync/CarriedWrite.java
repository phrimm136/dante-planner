package org.danteplanner.backend.planner.sync;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.PlannerStatus;

import java.util.UUID;

@Builder
public record CarriedWrite(
    String title,
    PlannerStatus status,
    String category,
    String content,
    Integer gameContentVersion,
    Integer contentSchemaVersion,
    UUID deviceId
) {
}
