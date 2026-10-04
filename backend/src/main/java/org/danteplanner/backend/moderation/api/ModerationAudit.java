package org.danteplanner.backend.moderation.api;

import java.util.Optional;
import java.util.UUID;

public interface ModerationAudit {

    void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType, String reason);

    Optional<String> latestBanReason(UUID targetPublicId);

    Optional<String> latestTimeoutReason(UUID targetPublicId);
}
