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

    public boolean carriesContent() {
        return content != null;
    }

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

    public UpsertPlannerRequest toUpsertRequest() {
        return new UpsertPlannerRequest(
                id, category, title, status, content, contentVersion, plannerType,
                syncVersion, selectedKeywords);
    }
}
