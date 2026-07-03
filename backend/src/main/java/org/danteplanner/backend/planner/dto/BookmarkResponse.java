package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Response DTO for bookmark toggle operations.
 *
 * @param plannerId  the planner ID that was bookmarked/unbookmarked
 * @param bookmarked current bookmark state after the toggle; true if now bookmarked
 */
@Builder
public record BookmarkResponse(
    UUID plannerId,
    boolean bookmarked
) {}
