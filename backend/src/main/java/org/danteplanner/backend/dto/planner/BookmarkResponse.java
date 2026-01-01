package org.danteplanner.backend.dto.planner;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for bookmark toggle operations.
 */
@Data
@Builder
public class BookmarkResponse {

    /**
     * The planner ID that was bookmarked/unbookmarked.
     */
    private UUID plannerId;

    /**
     * Current bookmark state after the toggle.
     * True if now bookmarked, false if now unbookmarked.
     */
    private boolean bookmarked;
}
