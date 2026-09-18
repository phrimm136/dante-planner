package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

public record CommentReportResponse(
    Instant createdAt
) {}
