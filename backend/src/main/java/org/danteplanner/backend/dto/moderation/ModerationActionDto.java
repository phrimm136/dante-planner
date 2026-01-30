package org.danteplanner.backend.dto.moderation;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.ModerationAction;

import java.time.Instant;

/**
 * DTO for moderation action with actor information.
 * Includes actor username (no internal IDs exposed).
 */
@Data
@Builder
public class ModerationActionDto {

    private String actionType;
    private String targetType;
    private String targetUuid;
    private String reason;
    private Integer durationMinutes;
    private Instant createdAt;
    private String actorUsernameEpithet;
    private String actorUsernameSuffix;

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
