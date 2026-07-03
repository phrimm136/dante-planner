package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Response DTO for subscription toggle operations.
 *
 * @param plannerId  the planner ID that was subscribed/unsubscribed
 * @param subscribed current subscription state after the toggle; true if subscribed (enabled)
 */
@Builder
public record SubscriptionResponse(
    UUID plannerId,
    boolean subscribed
) {}
