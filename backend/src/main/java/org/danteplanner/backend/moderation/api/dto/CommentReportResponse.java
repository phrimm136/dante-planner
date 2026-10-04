package org.danteplanner.backend.moderation.api.dto;

import java.time.Instant;

public record CommentReportResponse(
    Instant createdAt
) {}
