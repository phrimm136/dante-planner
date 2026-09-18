package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.sanitize.NotUserContent;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;
import org.danteplanner.backend.shared.util.PlannerConstants;

import java.util.Set;

public record UpsertPlannerRequest(
    @NotBlank(message = "ID is required")
    @NotUserContent
    String id,
    @NotBlank(message = "Category is required")
    @NotUserContent
    String category,
    @Size(max = PlannerConstants.TITLE_MAX_LENGTH,
        message = "Title must not exceed " + PlannerConstants.TITLE_MAX_LENGTH + " characters")
    @Sanitized(SanitizerKind.PLAIN)
    String title,
    PlannerStatus status,
    @NotNull(message = "Content is required")
    @Sanitized(SanitizerKind.PLANNER_CONTENT)
    String content,
    @NotNull(message = "Content version is required")
    @Positive(message = "Content version must be positive")
    Integer contentVersion,
    @NotNull(message = "Planner type is required")
    PlannerType plannerType,
    Long syncVersion,
    Set<String> selectedKeywords
) {
    public UpsertPlannerRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    public UpsertPlannerRequest withId(String id) {
        return new UpsertPlannerRequest(id, category, title, status, content, contentVersion,
                plannerType, null, selectedKeywords);
    }
}
