package org.danteplanner.backend.dto.comment;

import java.time.Instant;

/**
 * Response DTO for comment report operations.
 * Returns minimal data - frontend refetches after report.
 */
public record CommentReportResponse(
    Instant createdAt
) {}
