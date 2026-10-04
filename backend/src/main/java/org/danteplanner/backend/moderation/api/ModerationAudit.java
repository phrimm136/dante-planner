package org.danteplanner.backend.moderation.api;

public interface ModerationAudit {

    void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType, String reason);
}
