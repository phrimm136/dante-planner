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
    /** A null {@code selectedKeywords} is preserved: it means "leave unchanged", which an empty set does not. */
    public UpsertPlannerRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * This request rebound to the id the path carries. The sync version is dropped: the copy
     * addresses a planner that does not exist yet, so there is no stored version to compare against.
     *
     * @param id the planner id to bind
     * @return the rebound request
     */
    public UpsertPlannerRequest withId(String id) {
        return new UpsertPlannerRequest(id, category, title, status, content, contentVersion,
                plannerType, null, selectedKeywords);
    }
}
