package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

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
    private final ModerationPolicy moderationPolicy;

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
     * @throws UserNotFoundException if the actor or the target is not an active user
     * @throws InvalidRequestException if the duration is not positive
     * @throws ModerationForbiddenException if the actor may not restrict this target
     */
    @Transactional
    public User timeoutUser(Long actorId, Long targetId, int durationMinutes, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.TIMEOUT, reason, durationMinutes,
                target -> target.setTimeoutUntil(timeoutUntil(durationMinutes)));

        ssePublisher.publishAccountSuspended(
                targetId, reason, SuspensionType.TIMED_OUT, durationMinutes);

        log.info("User {} timed out until {} by moderator {}", targetId, saved.getTimeoutUntil(), actorId);
        return saved;
    }

    /**
     * Remove timeout from a user.
     *
     * @param actorId  the moderator/admin performing the action
     * @param targetId the user to remove timeout from
     * @param reason   reason for clearing timeout (for audit trail)
     * @return the updated user
     * @throws UserNotFoundException if the actor or the target is not an active user
     * @throws ModerationForbiddenException if the actor may not restrict this target
     */
    @Transactional
    public User removeTimeout(Long actorId, Long targetId, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.CLEAR_TIMEOUT, reason, null,
                target -> target.setTimeoutUntil(null));

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
     * @throws UserNotFoundException        if the actor or the target is not an active user
     * @throws ModerationForbiddenException if the actor may not restrict this target
     */
    @Transactional
    public User banUser(Long actorId, Long targetId, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.BAN, reason, null,
                target -> {
                    target.setBannedAt(Instant.now());
                    target.setBannedBy(actorId);
                });

        ssePublisher.publishAccountSuspended(targetId, reason, SuspensionType.BAN, null);

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
     * @throws UserNotFoundException        if the actor or the target is not an active user
     * @throws ModerationForbiddenException if the actor may not restrict this target
     */
    @Transactional
    public User unbanUser(Long actorId, Long targetId, String reason) {
        User saved = restrict(actorId, targetId, ModerationAction.ActionType.UNBAN, reason, null,
                target -> {
                    target.setBannedAt(null);
                    target.setBannedBy(null);
                });

        log.info("User {} unbanned by admin {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    /** Times out the user carrying a username suffix. */
    @Transactional
    public User timeoutUserBySuffix(Long actorId, String usernameSuffix, int durationMinutes, String reason) {
        return timeoutUser(actorId, targetIdBySuffix(usernameSuffix), durationMinutes, reason);
    }

    /** Removes the timeout from the user carrying a username suffix. */
    @Transactional
    public User removeTimeoutBySuffix(Long actorId, String usernameSuffix, String reason) {
        return removeTimeout(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    /** Bans the user carrying a username suffix. */
    @Transactional
    public User banUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return banUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    /** Unbans the user carrying a username suffix. */
    @Transactional
    public User unbanUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        return unbanUser(actorId, targetIdBySuffix(usernameSuffix), reason);
    }

    /**
     * Apply one account restriction: authorize the actor against the target, mutate the target, and
     * leave the audit record the action owes.
     */
    private User restrict(Long actorId, Long targetId, ModerationAction.ActionType action,
            String reason, Integer durationMinutes, Consumer<User> mutation) {
        User actor = requireActive(actorId);
        User target = requireActive(targetId);
        moderationPolicy.requireCanRestrict(actor, target, action);

        mutation.accept(target);
        User saved = userService.save(target);

        auditService.record(actorId, target.getPublicId().toString(), action,
                ModerationAction.TargetType.USER, reason, durationMinutes);

        return saved;
    }

    private Instant timeoutUntil(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new InvalidRequestException(
                    "INVALID_TIMEOUT_DURATION", "Timeout duration must be positive");
        }
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
