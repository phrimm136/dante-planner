package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationAudit;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
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
public class ModerationAuditService implements ModerationAudit {

    private final ModerationActionRepository moderationActionRepository;

    public void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType) {
        record(actorId, targetUuid, actionType, targetType, null, null);
    }

    @Override
    public void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType, String reason) {
        record(actorId, targetUuid, actionType, targetType, reason, null);
    }

    public void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType, String reason, Integer durationMinutes) {
        moderationActionRepository.insert(ModerationAction.builder()
                .actorId(actorId)
                .targetUuid(targetUuid)
                .actionType(actionType)
                .targetType(targetType)
                .reason(reason)
                .durationMinutes(durationMinutes)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> latestBanReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationActionType.BAN);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> latestTimeoutReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationActionType.TIMEOUT);
    }

    private Optional<String> latestReason(UUID targetPublicId, ModerationActionType type) {
        return moderationActionRepository
                .findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(targetPublicId.toString(), type)
                .map(ModerationAction::getReason);
    }
}
