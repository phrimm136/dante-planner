package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotNull;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.shared.sanitize.NotUserContent;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

import java.util.Set;

public record UpdatePlannerRequest(
    @Sanitized(SanitizerKind.PLAIN)
    String title,
    PlannerStatus status,
    @NotUserContent
    String category,
    @Sanitized(SanitizerKind.PLANNER_CONTENT)
    String content,
    @NotNull(message = "Sync version is required for optimistic locking")
    Long syncVersion,
    Set<String> selectedKeywords
) {
    public UpdatePlannerRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }
}
