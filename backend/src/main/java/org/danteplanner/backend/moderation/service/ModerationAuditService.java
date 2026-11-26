package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationAudit;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.user.api.RestrictionReasonProvider;
import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The moderation audit trail: single writer, and the restriction reasons read back off it.
 */
@Service
@RequiredArgsConstructor
public class ModerationAuditService implements ModerationAudit, RestrictionReasonProvider {

    private final ModerationActionRepository moderationActionRepository;

    /** A restriction change on an account, with the reason the moderator gave. */
    public sealed interface Suspension {

        String reason();

        /** Restricts the account for a number of minutes. */
        record Timeout(int durationMinutes, String reason) implements Suspension {
        }

        /** Ends a timeout early. */
        record ClearTimeout(String reason) implements Suspension {
        }

        /** Restricts the account until it is unbanned. */
        record Ban(String reason) implements Suspension {
        }

        /** Ends a ban. */
        record Unban(String reason) implements Suspension {
        }

        default ModerationActionType action() {
            return switch (this) {
                case Timeout timeout -> ModerationActionType.TIMEOUT;
                case ClearTimeout clear -> ModerationActionType.CLEAR_TIMEOUT;
                case Ban ban -> ModerationActionType.BAN;
                case Unban unban -> ModerationActionType.UNBAN;
            };
        }
    }

    public void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType) {
        insert(action(actorId, targetUuid, actionType, targetType));
    }

    @Override
    public void record(Long actorId, String targetUuid, ModerationActionType actionType,
            ModerationTargetType targetType, String reason) {
        insert(action(actorId, targetUuid, actionType, targetType).reason(reason));
    }

    public void record(Long actorId, String targetUuid, Suspension suspension) {
        ModerationAction.ModerationActionBuilder action =
                action(actorId, targetUuid, suspension.action(), ModerationTargetType.USER).reason(suspension.reason());
        insert(switch (suspension) {
            case Suspension.Timeout timeout -> action.durationMinutes(timeout.durationMinutes());
            case Suspension.ClearTimeout clear -> action;
            case Suspension.Ban ban -> action;
            case Suspension.Unban unban -> action;
        });
    }

    private static ModerationAction.ModerationActionBuilder action(Long actorId, String targetUuid,
            ModerationActionType actionType, ModerationTargetType targetType) {
        return ModerationAction.builder()
                .actorId(actorId)
                .targetUuid(targetUuid)
                .actionType(actionType)
                .targetType(targetType);
    }

    private void insert(ModerationAction.ModerationActionBuilder action) {
        moderationActionRepository.insert(action.build());
        FreshnessDeclarations.declare(new FreshnessScope.Global());
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Option<String> latestBanReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationActionType.BAN);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Option<String> latestTimeoutReason(UUID targetPublicId) {
        return latestReason(targetPublicId, ModerationActionType.TIMEOUT);
    }

    private Option<String> latestReason(UUID targetPublicId, ModerationActionType type) {
        return Option.from(moderationActionRepository
                        .findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(targetPublicId.toString(), type))
                .flatMap(action -> Option.fromNullable(action.getReason()));
    }
}
