package org.danteplanner.backend.comment.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

public record UpdateCommentResponse(
    @Nullable Instant editedAt
) {}
