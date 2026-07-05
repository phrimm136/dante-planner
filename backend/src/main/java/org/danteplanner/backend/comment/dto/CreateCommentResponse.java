package org.danteplanner.backend.comment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for comment creation.
 * Returns minimal data - frontend refetches the tree after creation.
 */
public record CreateCommentResponse(
    UUID id,
    Instant createdAt
) {}
