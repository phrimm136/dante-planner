package org.danteplanner.backend.dto.planner;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for fork operations.
 */
@Data
@Builder
public class ForkResponse {

    /**
     * The ID of the original planner that was forked.
     */
    private UUID originalPlannerId;

    /**
     * The ID of the newly created draft planner.
     */
    private UUID newPlannerId;

    /**
     * Human-readable message about the fork result.
     */
    private String message;
}
