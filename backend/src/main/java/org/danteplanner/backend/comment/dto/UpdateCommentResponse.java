package org.danteplanner.backend.comment.dto;

import java.time.Instant;

public record UpdateCommentResponse(
    Instant editedAt
) {}
