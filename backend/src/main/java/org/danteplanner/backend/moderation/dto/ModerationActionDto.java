package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import lombok.Builder;
import org.danteplanner.backend.moderation.entity.ModerationAction;

/**
 * DTO for moderation action with actor information.
 * Includes actor username (no internal IDs exposed).
 */
@Builder
public record ModerationActionDto(
    String actionType,
    String targetType,
    String targetUuid,
    String reason,
    Integer durationMinutes,
    Instant createdAt,
    String actorUsernameEpithet,
    String actorUsernameSuffix
) {

    /**
     * Convert entity to DTO with actor information.
     *
     * @param action the moderation action entity
     * @param actorEpithet the actor's username epithet
     * @param actorSuffix the actor's username suffix
     * @return the DTO
     */
    public static ModerationActionDto fromEntity(ModerationAction action, String actorEpithet, String actorSuffix) {
        return ModerationActionDto.builder()
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
