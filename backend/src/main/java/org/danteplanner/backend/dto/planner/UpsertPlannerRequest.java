package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.danteplanner.backend.entity.PlannerType;

@Data
public class UpsertPlannerRequest {

    /**
     * Client-generated UUID for the planner.
     * Server will use this ID instead of generating a new one.
     */
    @NotBlank(message = "ID is required")
    private String id;

    @NotBlank(message = "Category is required")
    private String category;

    /**
     * Title of the planner. Optional - if null, existing title is kept (update) or defaults to "Untitled" (create).
     */
    private String title;

    /**
     * Status of the planner. Optional - if null, existing status is kept (update) or defaults to "draft" (create).
     */
    private String status;

    @NotNull(message = "Content is required")
    private String content;

    /**
     * Game content version (e.g., 6 for MD6, 5 for RR5).
     * Must be provided from the config endpoint.
     */
    @NotNull(message = "Content version is required")
    @Positive(message = "Content version must be positive")
    private Integer contentVersion;

    /**
     * Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY).
     */
    @NotNull(message = "Planner type is required")
    private PlannerType plannerType;

    /**
     * Sync version for optimistic locking (upsert only).
     * Optional for create, required for update if not using force.
     */
    private Long syncVersion;
}
