package org.danteplanner.backend.comment.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateCommentResponse(
    UUID id,
    Instant createdAt
) {}
