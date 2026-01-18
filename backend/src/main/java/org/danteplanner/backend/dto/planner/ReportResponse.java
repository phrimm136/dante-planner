package org.danteplanner.backend.dto.planner;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for report submission operations.
 */
@Data
@Builder
public class ReportResponse {

    /**
     * The planner ID that was reported.
     */
    private UUID plannerId;

    /**
     * Confirmation message for the report submission.
     */
    private String message;
}
