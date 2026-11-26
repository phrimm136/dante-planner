package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.Device;
import org.danteplanner.backend.planner.entity.PlannerStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public record ContentWrite(
        String title,
        PlannerStatus status,
        String category,
        Set<String> keywords,
        String json,
        int schemaVersion,
        int gameContentVersion,
        Device device,
        long syncVersion,
        Instant savedAt) {

    public ContentWrite {
        keywords = Set.copyOf(keywords);
        savedAt = savedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
