package org.danteplanner.backend.moderation.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Acknowledgement of an action taken against a planner.
 *
 * @param plannerId the planner the action addressed
 * @param message   a human-readable description of the result
 */
@Builder
public record PlannerActionResponse(
    UUID plannerId,
    String message
) {}
