package org.danteplanner.backend.planner.sync;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Device;
import org.danteplanner.backend.planner.entity.PlannerStatus;


@Builder
public record CarriedWrite(
    String title,
    PlannerStatus status,
    String category,
    String content,
    Integer gameContentVersion,
    Integer contentSchemaVersion,
    Device device
) {
}
