package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.List;

/**
 * Response DTO for planner configuration.
 *
 * <p>Contains current versions for planner data format and game content.
 * schemaVersion is incremented when the planner data structure changes.
 * Content versions are updated when new game content is released.</p>
 *
 * @param schemaVersion       current planner data schema version, incremented on structure changes
 * @param mdCurrentVersion    current Mirror Dungeon version (e.g., 7 for MD7)
 * @param mdAvailableVersions available Mirror Dungeon versions for backward compatibility (e.g., [6, 7])
 * @param rrAvailableVersions available Refracted Railway versions (e.g., [1, 5] for RR1 and RR5)
 */
@Builder
public record PlannerConfigResponse(
    Integer schemaVersion,
    Integer mdCurrentVersion,
    List<Integer> mdAvailableVersions,
    List<Integer> rrAvailableVersions
) {}
