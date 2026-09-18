package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.event.AccountSuspendedEvent;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/**
 * Restrictions a moderator or admin places on a user account: timeout, ban, and their removal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserModerationService {

    private final UserService userService;
    private final ModerationAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModerationPolicy moderationPolicy;

    @Transactional
    public User timeoutUser(Long actorId, Long targetId, int durationMinutes, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.TIMEOUT, reason, durationMinutes,
                target -> target.setTimeoutUntil(timeoutUntil(durationMinutes)));

        eventPublisher.publishEvent(new AccountSuspendedEvent(
                targetId, reason, SuspensionType.TIMED_OUT, durationMinutes));

        log.info("User {} timed out until {} by moderator {}", targetId, saved.getTimeoutUntil(), actorId);
        return saved;
    }

    @Transactional
    public User removeTimeout(Long actorId, Long targetId, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.CLEAR_TIMEOUT, reason,
                target -> target.setTimeoutUntil(null));

        log.info("Timeout removed from user {} by moderator {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    @Transactional
    public User banUser(Long actorId, Long targetId, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.BAN, reason,
                target -> {
                    target.setBannedAt(Instant.now());
                    target.setBannedBy(actorId);
                });

        eventPublisher.publishEvent(AccountSuspendedEvent.ban(targetId, reason));

        log.info("User {} banned by admin {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    @Transactional
    public User unbanUser(Long actorId, Long targetId, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.UNBAN, reason,
                target -> {
                    target.setBannedAt(null);
                    target.setBannedBy(null);
                });

        log.info("User {} unbanned by admin {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    @Transactional
    public User timeoutUserBySuffix(Long actorId, String usernameSuffix, int durationMinutes, String reason) {
        return timeoutUser(actorId, targetIdBySuffix(usernameSuffix), durationMinutes, reason);
    }

    @Transactional
    public User removeTimeoutBySuffix(Long actorId, String usernameSuffix, String reason) {
        return removeTimeout(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    @Transactional
    public User banUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return banUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    @Transactional
    public User unbanUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return unbanUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    private User restrict(Long actorId, Long targetId, ModerationAction.ActionType action,
            String reason, Consumer<User> mutation) {
        return restrict(actorId, targetId, action, reason, null, mutation);
    }

    private User restrict(Long actorId, Long targetId, ModerationAction.ActionType action,
            String reason, Integer durationMinutes, Consumer<User> mutation) {
        User actor = requireActive(actorId);
        User target = requireActive(targetId);
        moderationPolicy.requireCanRestrict(actor, target, action);

        mutation.accept(target);

        auditService.record(actorId, target.getPublicId().toString(), action,
                ModerationAction.TargetType.USER, reason, durationMinutes);

        return target;
    }

    private Instant timeoutUntil(int durationMinutes) {
        return Instant.now().plus(durationMinutes, ChronoUnit.MINUTES);
    }

    private User requireActive(Long userId) {
        return userService.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Long targetIdBySuffix(String usernameSuffix) {
        return userService.findActiveBySuffix(usernameSuffix)
                .orElseThrow(() -> new UserNotFoundException(usernameSuffix))
                .getId();
    }
}
