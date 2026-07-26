package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Restrictions a moderator or admin places on a user account: timeout, ban, and their removal.
 *
 * <p>Every operation exists in two forms — by internal id, and by the username suffix the API
 * exposes — because moderation endpoints never carry a raw numeric user id.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserModerationService {

    private final UserService userService;
    private final ModerationAuditService auditService;
    private final SsePublisher ssePublisher;

    /**
     * Timeout a user for a specified duration.
     * Timed-out users cannot publish a planner, comment, reply, or edit a comment. Private planner
     * work, voting, and reporting stay available.
     *
     * @param actorId         the moderator/admin performing the action
     * @param targetId        the user to timeout
     * @param durationMinutes duration in minutes
     * @param reason          reason for timeout (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException if target user not found
     * @throws IllegalArgumentException if the duration is not positive
     * @throws ModerationForbiddenException if the actor may not restrict this target
     */
    @Transactional
    public User timeoutUser(Long actorId, Long targetId, int durationMinutes, String reason) {
        User actor = requireActive(actorId);
        User target = requireActive(targetId);

        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Timeout duration must be positive");
        }

        if (target.getRole() == UserRole.ADMIN) {
            throw new ModerationForbiddenException("Cannot timeout administrators");
        }

        if (actor.getRole() == UserRole.MODERATOR && target.getRole() == UserRole.MODERATOR) {
            throw new ModerationForbiddenException("Moderators cannot timeout other moderators");
        }

        Instant timeoutUntil = Instant.now().plus(durationMinutes, ChronoUnit.MINUTES);
        target.setTimeoutUntil(timeoutUntil);
        User saved = userService.saveRestriction(target);

        auditService.record(actorId, target.getPublicId().toString(), ModerationAction.ActionType.TIMEOUT,
                ModerationAction.TargetType.USER, reason, durationMinutes);

        ssePublisher.publishAccountSuspended(targetId, reason, "TIMEOUT", durationMinutes);

        log.info("User {} timed out until {} by moderator {}", targetId, timeoutUntil, actorId);
        return saved;
    }

    /**
     * Remove timeout from a user.
     *
     * @param actorId  the moderator/admin performing the action
     * @param targetId the user to remove timeout from
     * @param reason   reason for clearing timeout (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException if target user not found
     */
    @Transactional
    public User removeTimeout(Long actorId, Long targetId, String reason) {
        User target = requireActive(targetId);

        target.setTimeoutUntil(null);
        User saved = userService.saveRestriction(target);

        auditService.record(actorId, target.getPublicId().toString(), ModerationAction.ActionType.CLEAR_TIMEOUT,
                ModerationAction.TargetType.USER, reason, null);

        log.info("Timeout removed from user {} by moderator {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    /**
     * Ban a user permanently.
     * A ban withdraws everything a timeout does, plus voting and reporting: a banned user may not act
     * on anyone else's content. Private planner work stays available.
     *
     * @param actorId  the admin performing the action
     * @param targetId the user to ban
     * @param reason   reason for ban (optional)
     * @return the updated user
     * @throws UserNotFoundException        if target user not found
     * @throws ModerationForbiddenException if the actor is not an administrator, or the target is
     */
    @Transactional
    public User banUser(Long actorId, Long targetId, String reason) {
        User actor = requireActive(actorId);
        User target = requireActive(targetId);

        if (actor.getRole() != UserRole.ADMIN) {
            throw new ModerationForbiddenException("Only administrators can ban users");
        }

        if (target.getRole() == UserRole.ADMIN) {
            throw new ModerationForbiddenException("Cannot ban administrators");
        }

        Instant now = Instant.now();
        target.setBannedAt(now);
        target.setBannedBy(actorId);
        User saved = userService.saveRestriction(target);

        auditService.record(actorId, target.getPublicId().toString(), ModerationAction.ActionType.BAN,
                ModerationAction.TargetType.USER, reason, null);

        ssePublisher.publishAccountSuspended(targetId, reason, "BAN", null);

        log.info("User {} banned by admin {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    /**
     * Unban a user.
     *
     * @param actorId  the admin performing the action
     * @param targetId the user to unban
     * @param reason   reason for unbanning (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException        if target user not found
     * @throws ModerationForbiddenException if the actor is not an administrator
     */
    @Transactional
    public User unbanUser(Long actorId, Long targetId, String reason) {
        User actor = requireActive(actorId);
        User target = requireActive(targetId);

        if (actor.getRole() != UserRole.ADMIN) {
            throw new ModerationForbiddenException("Only administrators can unban users");
        }

        target.setBannedAt(null);
        target.setBannedBy(null);
        User saved = userService.saveRestriction(target);

        auditService.record(actorId, target.getPublicId().toString(), ModerationAction.ActionType.UNBAN,
                ModerationAction.TargetType.USER, reason, null);

        log.info("User {} unbanned by admin {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    /**
     * Timeout the user carrying a username suffix.
     *
     * @param actorId         the moderator/admin user ID
     * @param usernameSuffix  the username suffix of the user to timeout
     * @param durationMinutes timeout duration
     * @param reason          reason for timeout (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException if no active user carries the suffix
     */
    @Transactional
    public User timeoutUserBySuffix(Long actorId, String usernameSuffix, int durationMinutes, String reason) {
        return timeoutUser(actorId, targetIdBySuffix(usernameSuffix), durationMinutes, reason);
    }

    /**
     * Remove the timeout from the user carrying a username suffix.
     *
     * @param actorId        the moderator/admin user ID
     * @param usernameSuffix the username suffix of the user to clear timeout
     * @param reason         reason for clearing timeout (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException if no active user carries the suffix
     */
    @Transactional
    public User removeTimeoutBySuffix(Long actorId, String usernameSuffix, String reason) {
        return removeTimeout(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    /**
     * Ban the user carrying a username suffix.
     *
     * @param actorId        the admin user ID
     * @param usernameSuffix the username suffix of the user to ban
     * @param reason         ban reason (optional)
     * @return the updated user
     * @throws UserNotFoundException if no active user carries the suffix
     */
    @Transactional
    public User banUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return banUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    /**
     * Unban the user carrying a username suffix.
     *
     * @param actorId        the admin user ID
     * @param usernameSuffix the username suffix of the user to unban
     * @param reason         reason for unbanning (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException if no active user carries the suffix
     */
    @Transactional
    public User unbanUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return unbanUser(actorId, targetIdBySuffix(usernameSuffix), reason);
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
