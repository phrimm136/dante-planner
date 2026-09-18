package org.danteplanner.backend.user.dto;

import java.time.Instant;

public record UserDeletionResponse(
    String message,
    Instant deletedAt,
    Instant permanentDeleteAt,
    int gracePeriodDays
) {}
