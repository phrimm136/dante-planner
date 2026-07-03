package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotNull;

import org.danteplanner.backend.planner.entity.PlannerStatus;

import java.util.Set;

/**
 * Request DTO for partial planner updates. Null fields are left unchanged.
 *
 * @param title            updated title; null leaves the title unchanged
 * @param status           updated status; null leaves the status unchanged
 * @param category         updated category; null leaves the category unchanged. Must be valid for
 *                         the planner's type (MD categories for MIRROR_DUNGEON, RR for REFRACTED_RAILWAY)
 * @param content          updated content (JSON string); null leaves the content unchanged
 * @param syncVersion      required for optimistic locking; must match the current syncVersion
 * @param selectedKeywords updated selected keywords; null leaves the keywords unchanged
 */
public record UpdatePlannerRequest(
    String title,
    PlannerStatus status,
    String category,
    String content,
    @NotNull(message = "Sync version is required for optimistic locking")
    Long syncVersion,
    Set<String> selectedKeywords
) {
    public UpdatePlannerRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }
}
