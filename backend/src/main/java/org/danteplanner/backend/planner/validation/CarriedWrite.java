package org.danteplanner.backend.planner.validation;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.PlannerStatus;

import java.util.Set;
import java.util.UUID;

/**
 * The values a write request would apply to a planner's content row. A null component means the
 * request states nothing about that field, so applying it leaves the stored value alone.
 *
 * @param title                the title to apply
 * @param status               the status to apply
 * @param category             the category to apply
 * @param content              the content document to apply, in the form the sanitizer produced
 * @param gameContentVersion   the game content version to apply
 * @param contentSchemaVersion the content schema version the write path would stamp
 * @param selectedKeywords     the keywords to apply, in the form the client sent them
 * @param deviceId             the device stamp to apply
 */
@Builder
public record CarriedWrite(
    String title,
    PlannerStatus status,
    String category,
    String content,
    Integer gameContentVersion,
    Integer contentSchemaVersion,
    Set<String> selectedKeywords,
    UUID deviceId
) {
}
