package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.util.Set;

/**
 * Request DTO for creating or updating a planner via upsert.
 *
 * @param id               client-generated UUID; the server uses this ID instead of generating one
 * @param category         planner category
 * @param title            planner title; null keeps the existing title (update) or defaults to "Untitled" (create)
 * @param status           planner status; null keeps the existing status (update) or defaults to "draft" (create)
 * @param content          planner content (JSON string)
 * @param contentVersion   game content version (e.g., 6 for MD6, 5 for RR5); provided by the config endpoint
 * @param plannerType      type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY)
 * @param syncVersion      sync version for optimistic locking; optional for create, required for update unless forced
 * @param selectedKeywords selected keywords for list-view display (MD planners only)
 */
public record UpsertPlannerRequest(
    @NotBlank(message = "ID is required")
    String id,
    @NotBlank(message = "Category is required")
    String category,
    String title,
    PlannerStatus status,
    @NotNull(message = "Content is required")
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
}
