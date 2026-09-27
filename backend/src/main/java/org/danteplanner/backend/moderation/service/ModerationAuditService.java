package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Optional<String> latestBanReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationAction.ActionType.BAN);
    }

    @Transactional(readOnly = true)
    public Optional<String> latestTimeoutReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationAction.ActionType.TIMEOUT);
    }

    private Optional<String> latestReason(UUID targetPublicId, ModerationAction.ActionType type) {
        return moderationActionRepository
                .findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(targetPublicId.toString(), type)
                .map(ModerationAction::getReason);
    }
}
