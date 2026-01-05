package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.danteplanner.backend.entity.PlannerType;

@Data
public class CreatePlannerRequest {

    @NotBlank(message = "Category is required")
    private String category;

    /**
     * Title of the planner. Defaults to "Untitled" if not provided.
     */
    private String title = "Untitled";

    /**
     * Status of the planner. Defaults to "draft" if not provided.
     */
    private String status = "draft";

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
}
