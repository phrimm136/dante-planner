package org.danteplanner.backend.dto.user;

import java.time.Instant;

/**
 * Response DTO for account deletion requests.
 * Contains information about the scheduled deletion and grace period.
 *
 * @param message              Human-readable status message
 * @param deletedAt            When the account was marked as deleted
 * @param permanentDeleteAt    When the account will be permanently deleted
 * @param gracePeriodDays      Number of days in the grace period for reactivation
 */
public record UserDeletionResponse(
    String message,
    Instant deletedAt,
    Instant permanentDeleteAt,
    int gracePeriodDays
) {}
