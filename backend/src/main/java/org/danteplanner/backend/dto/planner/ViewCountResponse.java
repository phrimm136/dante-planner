package org.danteplanner.backend.dto.planner;

/**
 * Response DTO for view recording operations.
 * Returns the updated view count after recording a view.
 */
public record ViewCountResponse(
    int viewCount
) {
}
