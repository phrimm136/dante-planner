package org.danteplanner.backend.planner.dto;

import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.sanitize.NotUserContent;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

/**
 * Request DTO for the superseded state-setting publish route.
 *
 * <p>Names the desired publication state and optionally carries the document to upsert before
 * reaching it. Kept only for tabs running a bundle published before the intent routes existed; the
 * content fields are validated as a group, since a state-only request carries none of them.</p>
 *
 * @param published        the desired publication state
 * @param id               client-generated UUID; the server uses this ID instead of generating one
 * @param category         planner category
 * @param title            planner title
 * @param status           planner status
 * @param content          planner content (JSON string); its presence marks a content-carrying request
 * @param contentVersion   game content version provided by the config endpoint
 * @param plannerType      type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY)
 * @param syncVersion      sync version for optimistic locking
 * @param selectedKeywords selected keywords for list-view display (MD planners only)
 * @deprecated superseded by the publish and unpublish intent routes, which carry the intent in the
 *     path instead of the body.
 */
@Deprecated(forRemoval = true)
public record LegacyPublishRequest(
    @NotNull(message = "published is required")
    Boolean published,
    @NotUserContent
    String id,
    @NotUserContent
    String category,
    @Sanitized(SanitizerKind.PLAIN)
    String title,
    PlannerStatus status,
    @Sanitized(SanitizerKind.PLANNER_CONTENT)
    String content,
    Integer contentVersion,
    PlannerType plannerType,
    Long syncVersion,
    Set<String> selectedKeywords
) {
    public LegacyPublishRequest {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * Whether the request carries a document to upsert before applying the publication state.
     */
    public boolean carriesContent() {
        return content != null;
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
