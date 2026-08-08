package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * The moderation audit trail: single writer, and the restriction reasons read back off it.
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
     * Record one moderator action that carries no duration.
     *
     * @param actorId    the moderator or admin performing the action
     * @param targetUuid the public id of the affected user, planner, or comment
     * @param actionType what was done
     * @param targetType what kind of entity it was done to
     * @param reason     free-text justification, may be null
     */
    public void record(Long actorId, String targetUuid, ModerationAction.ActionType actionType,
            ModerationAction.TargetType targetType, String reason) {
        record(actorId, targetUuid, actionType, targetType, reason, null);
    }

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
        moderationActionRepository.insert(ModerationAction.builder()
                .actorId(actorId)
                .targetUuid(targetUuid)
                .actionType(actionType)
                .targetType(targetType)
                .reason(reason)
                .durationMinutes(durationMinutes)
                .build());
    }

    /**
     * The reason recorded for the ban an account currently carries.
     *
     * @param targetPublicId the account's public id
     * @return the newest ban reason, or empty if the trail holds none
     */
    public Optional<String> latestBanReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationAction.ActionType.BAN);
    }

    /**
     * The reason recorded for the timeout an account currently carries.
     *
     * @param targetPublicId the account's public id
     * @return the newest timeout reason, or empty if the trail holds none
     */
    public Optional<String> latestTimeoutReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationAction.ActionType.TIMEOUT);
    }

    /**
     * Unannotated like {@link #record}: a caller rendering an account it just restricted would read
     * a replica under readOnly routing and miss the record its own transaction wrote.
     *
     * <p>Returns the reason rather than the record so a caller outside moderation never takes on
     * {@link ModerationAction}'s shape.</p>
     */
    private Optional<String> latestReason(UUID targetPublicId, ModerationAction.ActionType type) {
        return moderationActionRepository
                .findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(targetPublicId.toString(), type)
                .map(ModerationAction::getReason);
    }
}
