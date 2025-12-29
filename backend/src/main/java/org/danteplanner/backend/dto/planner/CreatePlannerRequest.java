package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.danteplanner.backend.entity.MDCategory;

@Data
public class CreatePlannerRequest {

    @NotNull(message = "Category is required")
    private MDCategory category;

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
}
