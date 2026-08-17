package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;

/**
 * The payload of the site-wide SSE broadcast raised when a planner is published for the first
 * time.
 *
 * @param plannerId     the newly published planner
 * @param plannerTitle  the planner title for display
 * @param authorEpithet the author's username epithet, null when the account is gone
 * @param authorSuffix  the author's username suffix, null when the account is gone
 */
public record PlannerPublishedPayload(
    String plannerId,
    String plannerTitle,
    String authorEpithet,
    String authorSuffix
) {

    /**
     * Projects a published planner onto its broadcast payload.
     *
     * @param planner the published planner, with its author loaded
     * @return the broadcast payload
     */
    public static PlannerPublishedPayload fromEntity(Planner planner) {
        User author = planner.getUser();
        return new PlannerPublishedPayload(
                planner.getId().toString(),
                planner.getTitle(),
                author == null ? null : author.getUsernameEpithet(),
                author == null ? null : author.getUsernameSuffix());
    }
}
