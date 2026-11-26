package org.danteplanner.backend.planner.repository;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.PlannerStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

public record ContentWrite(
        String title,
        PlannerStatus status,
        String category,
        Set<String> keywords,
        String json,
        int schemaVersion,
        int gameContentVersion,
        @Nullable UUID deviceId,
        long syncVersion,
        Instant savedAt) {

    public ContentWrite {
        keywords = Set.copyOf(keywords);
        savedAt = savedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
