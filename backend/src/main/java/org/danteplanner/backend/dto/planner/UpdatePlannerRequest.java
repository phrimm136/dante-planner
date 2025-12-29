package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePlannerRequest {

    /**
     * Updated title. If null, title is not updated.
     */
    private String title;

    /**
     * Updated status. If null, status is not updated.
     */
    private String status;

    /**
     * Updated content (JSON string). If null, content is not updated.
     */
    private String content;

    /**
     * Required for optimistic locking. Must match current syncVersion.
     */
    @NotNull(message = "Sync version is required for optimistic locking")
    private Long syncVersion;
}
