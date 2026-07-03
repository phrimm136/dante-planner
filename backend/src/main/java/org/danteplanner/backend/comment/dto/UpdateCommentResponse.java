package org.danteplanner.backend.comment.dto;

import java.time.Instant;

/**
 * Response DTO for comment update operations.
 * Returns minimal data - frontend refetches after update.
 */
public record UpdateCommentResponse(
    Instant editedAt
) {}
