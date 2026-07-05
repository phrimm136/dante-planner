package org.danteplanner.backend.moderation.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Response DTO for report submission operations.
 *
 * @param plannerId the planner ID that was reported
 * @param message   confirmation message for the report submission
 */
@Builder
public record ReportResponse(
    UUID plannerId,
    String message
) {}
