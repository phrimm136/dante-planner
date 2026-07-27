package org.danteplanner.backend.planner.dto;

import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

/**
 * Request DTO for the publish endpoint.
 *
 * <p>Names the desired publication state and optionally carries the document to upsert before
 * reaching it, so one request can create a client-side draft and publish it. A request that omits
 * {@code published} is the legacy toggle shape kept for tabs running a previously cached bundle;
 * the content fields are otherwise validated as a group, since a state-only request carries none
 * of them.</p>
 *
 * @param published        the desired publication state; absent means the legacy toggle
 * @param id               client-generated UUID; the server uses this ID instead of generating one
 * @param category         planner category
 * @param title            planner title
 * @param status           planner status
 * @param content          planner content (JSON string); its presence marks a content-carrying request
 * @param contentVersion   game content version provided by the config endpoint
 * @param plannerType      type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY)
 * @param syncVersion      sync version for optimistic locking
 * @param selectedKeywords selected keywords for list-view display (MD planners only)
 */
public record PublishRequest(
    @NotNull(message = "published is required")
    Boolean published,
    String id,
    String category,
    String title,
    PlannerStatus status,
    String content,
    Integer contentVersion,
    PlannerType plannerType,
    Long syncVersion,
    Set<String> selectedKeywords
) {
    public PublishRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * Whether the request carries a document to upsert before applying the publication state.
     */
    public boolean carriesContent() {
        return content != null;
    }

    /**
     * Whether the request names an explicit publication state rather than asking for a toggle.
     */
    public boolean namesState() {
        return published != null;
    }

    /**
     * Holds when the request carries either no content at all or a complete content payload,
     * mirroring the field-level constraints of {@link UpsertPlannerRequest}, which cannot be
     * applied directly here because a state-only request legitimately omits every content field.
     */
    @AssertTrue(message = "Content payload is incomplete")
    public boolean isContentPayloadComplete() {
        if (!carriesContent()) {
            return id == null && category == null && contentVersion == null && plannerType == null;
        }
        return id != null && !id.isBlank()
                && category != null && !category.isBlank()
                && contentVersion != null && contentVersion > 0
                && plannerType != null;
    }

    /**
     * The content-carrying half of this request as an upsert payload.
     */
    public UpsertPlannerRequest toUpsertRequest() {
        return new UpsertPlannerRequest(
                id, category, title, status, content, contentVersion, plannerType,
                syncVersion, selectedKeywords);
    }
}
