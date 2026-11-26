package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.service.ModerationAuditService.Suspension;
import org.danteplanner.backend.moderation.service.ModerationPolicy.PermittedRestriction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.event.AccountSuspendedEvent;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.danteplanner.backend.shared.restriction.Restriction;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.service.UserService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final Clock clock;

    /** The restriction a moderation command left on an account. */
    public record RestrictedAccount(
            String usernameSuffix,
            @Nullable Instant timeoutUntil,
            Restriction restriction) {
    }

    @Transactional
    public RestrictedAccount timeoutUser(Long actorId, Long targetId, int durationMinutes, String reason) {
        Instant until = timeoutUntil(durationMinutes);
        User restricted = restrict(actorId, targetId, new Suspension.Timeout(durationMinutes, reason),
                target -> userService.timeOut(target, until));

        eventPublisher.publishEvent(new AccountSuspendedEvent(
                targetId, reason, SuspensionType.TIMED_OUT, durationMinutes));

        log.info("User {} timed out until {} by moderator {}", targetId, until, actorId);
        return account(restricted, restricted.getBannedAt(), until);
    }

    @Transactional
    public RestrictedAccount removeTimeout(Long actorId, Long targetId, String reason) {
        User restricted = restrict(actorId, targetId, new Suspension.ClearTimeout(reason),
                target -> userService.clearTimeout(target));

        log.info("Timeout removed from user {} by moderator {} with reason: {}", targetId, actorId, reason);
        return account(restricted, restricted.getBannedAt(), null);
    }

    @Transactional
    public RestrictedAccount banUser(Long actorId, Long targetId, String reason) {
        Instant at = clock.instant();
        User restricted = restrict(actorId, targetId, new Suspension.Ban(reason),
                target -> userService.ban(target, actorId, at));

        eventPublisher.publishEvent(AccountSuspendedEvent.ban(targetId, reason));

        log.info("User {} banned by admin {} with reason: {}", targetId, actorId, reason);
        return account(restricted, at, restricted.getTimeoutUntil());
    }

    @Transactional
    public RestrictedAccount unbanUser(Long actorId, Long targetId, String reason) {
        User restricted = restrict(actorId, targetId, new Suspension.Unban(reason),
                target -> userService.unban(target));

        log.info("User {} unbanned by admin {} with reason: {}", targetId, actorId, reason);
        return account(restricted, null, restricted.getTimeoutUntil());
    }

    @Transactional
    public RestrictedAccount timeoutUserBySuffix(Long actorId, String usernameSuffix, int durationMinutes, String reason) {
        return timeoutUser(actorId, targetIdBySuffix(usernameSuffix), durationMinutes, reason);
    }

    @Transactional
    public RestrictedAccount removeTimeoutBySuffix(Long actorId, String usernameSuffix, String reason) {
        return removeTimeout(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    @Transactional
    public RestrictedAccount banUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return banUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    @Transactional
    public RestrictedAccount unbanUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return unbanUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    private User restrict(Long actorId, Long targetId, Suspension suspension, Consumer<User> command) {
        User actor = requireActive(actorId);
        PermittedRestriction permitted = moderationPolicy.requireCanRestrict(
                actor, requireActive(targetId), suspension.action());
        User target = permitted.target();

        command.accept(target);

        auditService.record(actorId, target.getPublicId().toString(), suspension);

        return target;
    }

    private RestrictedAccount account(User target, @Nullable Instant bannedAt, @Nullable Instant timeoutUntil) {
        return new RestrictedAccount(target.getUsernameSuffix(), timeoutUntil,
                Restriction.of(bannedAt, timeoutUntil, clock.instant()));
    }

    private Instant timeoutUntil(int durationMinutes) {
        return clock.instant().plus(durationMinutes, ChronoUnit.MINUTES);
    }

    private User requireActive(Long userId) {
        return userService.findActiveById(userId).value()
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Long targetIdBySuffix(String usernameSuffix) {
        return userService.findActiveBySuffix(usernameSuffix).getId();
    }
}
