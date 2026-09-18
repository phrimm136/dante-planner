package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import lombok.Builder;
import org.danteplanner.backend.moderation.entity.ModerationAction;

@Builder
public record ModerationActionResponse(
    String actionType,
    String targetType,
    String targetUuid,
    String reason,
    int durationMinutes,
    Instant createdAt,
    String actorUsernameEpithet,
    String actorUsernameSuffix
) {

    public static ModerationActionResponse fromEntity(ModerationAction action, String actorEpithet, String actorSuffix) {
        return ModerationActionResponse.builder()
                .actionType(action.getActionType().name())
                .targetType(action.getTargetType().name())
                .targetUuid(action.getTargetUuid() != null ? action.getTargetUuid() : "")
                .reason(action.getReason() != null ? action.getReason() : "")
                .durationMinutes(action.getDurationMinutes() != null ? action.getDurationMinutes() : 0)
                .createdAt(action.getCreatedAt())
                .actorUsernameEpithet(actorEpithet)
                .actorUsernameSuffix(actorSuffix)
                .build();
    }
}
