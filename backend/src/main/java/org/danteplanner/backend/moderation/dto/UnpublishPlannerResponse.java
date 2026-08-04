package org.danteplanner.backend.moderation.dto;

import java.util.UUID;

/**
 * Response DTO for a moderator unpublishing a planner.
 *
 * @param plannerId the planner that was unpublished
 * @param published the planner's publication state after the action
 * @param message   a human-readable message describing the operation result
 */
public record UnpublishPlannerResponse(UUID plannerId, boolean published, String message) {
}
