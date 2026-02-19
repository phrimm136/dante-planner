package org.danteplanner.backend.dto.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for planner configuration.
 *
 * <p>Contains current versions for planner data format and game content.
 * schemaVersion is incremented when the planner data structure changes.
 * Content versions are updated when new game content is released.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerConfigResponse {

    /**
     * Current planner data schema version (e.g., 1, 2, 3)
     * Incremented when planner data structure changes for migration support
     */
    private Integer schemaVersion;

    /**
     * Current Mirror Dungeon version (e.g., 7 for MD7)
     */
    private Integer mdCurrentVersion;

    /**
     * Available Mirror Dungeon versions for backward compatibility (e.g., [6, 7])
     * Includes current version and legacy versions still supported for updates
     */
    private List<Integer> mdAvailableVersions;

    /**
     * Available Refracted Railway versions (e.g., [1, 5] for RR1 and RR5)
     * Multiple versions can coexist
     */
    private List<Integer> rrAvailableVersions;
}
