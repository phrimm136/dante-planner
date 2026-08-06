package org.danteplanner.backend.planner.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

/**
 * Request DTO for the publish and unpublish intent endpoints.
 *
 * <p>Carries the document to store before the planner reaches the requested state, so a client-side
 * draft costs one round trip. The intent lives in the route, so this body names only the document;
 * a request that sends no body at all publishes or withdraws what the server already stores.</p>
 *
 * @param id               client-generated UUID; the server uses this ID instead of generating one
 * @param category         planner category
 * @param title            planner title
 * @param status           planner status
 * @param content          planner content (JSON string)
 * @param contentVersion   game content version provided by the config endpoint
 * @param plannerType      type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY)
 * @param syncVersion      sync version for optimistic locking
 * @param selectedKeywords selected keywords for list-view display (MD planners only)
 */
public record PublishRequest(
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
    public PublishRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * This request as the upsert payload the command service stores.
     */
    public UpsertPlannerRequest toUpsertRequest() {
        return new UpsertPlannerRequest(
                id, category, title, status, content, contentVersion, plannerType,
                syncVersion, selectedKeywords);
    }
}
