package org.danteplanner.backend.dto.planner;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for subscription toggle operations.
 */
@Data
@Builder
public class SubscriptionResponse {

    /**
     * The planner ID that was subscribed/unsubscribed.
     */
    private UUID plannerId;

    /**
     * Current subscription state after the toggle.
     * True if subscribed (enabled), false if unsubscribed (disabled).
     */
    private boolean subscribed;
}
