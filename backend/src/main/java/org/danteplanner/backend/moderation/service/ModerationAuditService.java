package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * The moderation audit trail: single writer, and the restriction reasons read back off it.
 */
@Service
@RequiredArgsConstructor
public class ModerationAuditService {

    private final ModerationActionRepository moderationActionRepository;

    public void record(Long actorId, String targetUuid, ModerationAction.ActionType actionType,
            ModerationAction.TargetType targetType) {
        record(actorId, targetUuid, actionType, targetType, null, null);
    }

    public void record(Long actorId, String targetUuid, ModerationAction.ActionType actionType,
            ModerationAction.TargetType targetType, String reason) {
        record(actorId, targetUuid, actionType, targetType, reason, null);
    }

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

    public Optional<String> latestBanReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationAction.ActionType.BAN);
    }

    public Optional<String> latestTimeoutReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationAction.ActionType.TIMEOUT);
    }

    /**
     * Unannotated like {@link #record}: a caller rendering an account it just restricted would read
     * a replica under readOnly routing and miss the record its own transaction wrote.
     */
    private Optional<String> latestReason(UUID targetPublicId, ModerationAction.ActionType type) {
        return moderationActionRepository
                .findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(targetPublicId.toString(), type)
                .map(ModerationAction::getReason);
    }
}
