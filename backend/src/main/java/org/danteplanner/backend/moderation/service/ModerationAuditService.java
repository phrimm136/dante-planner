package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.springframework.stereotype.Service;

/**
 * Single writer for the moderation audit trail.
 *
 * <p>Deliberately synchronous and inside the caller's transaction: an action that commits without
 * its record is the failure this exists to prevent, so an after-commit listener would be the wrong
 * seam. Callers outside the moderation feature reach the trail through here rather than through the
 * repository.</p>
 */
@Service
@RequiredArgsConstructor
public class ModerationAuditService {

    private final ModerationActionRepository moderationActionRepository;

    /**
     * Record one moderator action.
     *
     * @param actorId         the moderator or admin performing the action
     * @param targetUuid      the public id of the affected user, planner, or comment
     * @param actionType      what was done
     * @param targetType      what kind of entity it was done to
     * @param reason          free-text justification, may be null
     * @param durationMinutes duration for time-bounded actions, null otherwise
     */
    public void record(Long actorId, String targetUuid, ModerationAction.ActionType actionType,
            ModerationAction.TargetType targetType, String reason, Integer durationMinutes) {
        moderationActionRepository.save(ModerationAction.builder()
                .actorId(actorId)
                .targetUuid(targetUuid)
                .actionType(actionType)
                .targetType(targetType)
                .reason(reason)
                .durationMinutes(durationMinutes)
                .build());
    }
}
