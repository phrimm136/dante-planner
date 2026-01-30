package org.danteplanner.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.HidePlannerRequest;
import org.danteplanner.backend.dto.planner.ModerationResponse;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.ModerationAction;
import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.CommentNotFoundException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.ModerationActionRepository;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for moderation operations.
 * MODERATOR and ADMIN users can perform these operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final UserRepository userRepository;
    private final PlannerRepository plannerRepository;
    private final PlannerCommentRepository plannerCommentRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final SseService sseService;

    /**
     * Timeout a user for a specified duration.
     * Timed-out users cannot create, edit, or publish planners.
     *
     * @param actorId         the moderator/admin performing the action
     * @param targetId        the user to timeout
     * @param durationMinutes duration in minutes
     * @return the updated user
     * @throws UserNotFoundException if target user not found
     * @throws IllegalArgumentException if trying to timeout an admin or invalid duration
     */
    @Transactional
    public User timeoutUser(Long actorId, Long targetId, int durationMinutes, String reason) {
        User actor = userRepository.findByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // Validate duration
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Timeout duration must be positive");
        }

        // Cannot timeout admins
        if (target.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Cannot timeout administrators");
        }

        // Moderators can only timeout NORMAL users, not other moderators
        if (actor.getRole() == UserRole.MODERATOR && target.getRole() == UserRole.MODERATOR) {
            throw new IllegalArgumentException("Moderators cannot timeout other moderators");
        }

        Instant timeoutUntil = Instant.now().plus(durationMinutes, ChronoUnit.MINUTES);
        target.setTimeoutUntil(timeoutUntil);
        User saved = userRepository.save(target);

        // Log to audit trail with reason
        logModerationAction(actorId, target.getPublicId().toString(), ModerationAction.ActionType.TIMEOUT, ModerationAction.TargetType.USER, reason, durationMinutes);

        // Notify user via SSE
        sseService.notifyAccountSuspended(targetId, reason, "TIMEOUT", durationMinutes);

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
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        target.setTimeoutUntil(null);
        User saved = userRepository.save(target);

        // Log to audit trail with reason
        logModerationAction(actorId, target.getPublicId().toString(), ModerationAction.ActionType.CLEAR_TIMEOUT, ModerationAction.TargetType.USER, reason, null);

        log.info("Timeout removed from user {} by moderator {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    /**
     * Ban a user permanently.
     * Banned users cannot create, edit, publish planners, or submit comments.
     *
     * @param actorId  the admin performing the action
     * @param targetId the user to ban
     * @param reason   reason for ban (optional)
     * @return the updated user
     * @throws UserNotFoundException    if target user not found
     * @throws IllegalArgumentException if trying to ban another admin
     */
    @Transactional
    public User banUser(Long actorId, Long targetId, String reason) {
        User actor = userRepository.findByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // Only admins can ban
        if (actor.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Only administrators can ban users");
        }

        // Cannot ban other admins
        if (target.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Cannot ban administrators");
        }

        Instant now = Instant.now();
        target.setBannedAt(now);
        target.setBannedBy(actorId);
        User saved = userRepository.save(target);

        // Log to audit trail
        logModerationAction(actorId, target.getPublicId().toString(), ModerationAction.ActionType.BAN, ModerationAction.TargetType.USER, reason, null);

        // Notify user via SSE
        sseService.notifyAccountSuspended(targetId, reason, "BAN", null);

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
     * @throws UserNotFoundException if target user not found
     */
    @Transactional
    public User unbanUser(Long actorId, Long targetId, String reason) {
        User actor = userRepository.findByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // Only admins can unban
        if (actor.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Only administrators can unban users");
        }

        target.setBannedAt(null);
        target.setBannedBy(null);
        User saved = userRepository.save(target);

        // Log to audit trail with reason
        logModerationAction(actorId, target.getPublicId().toString(), ModerationAction.ActionType.UNBAN, ModerationAction.TargetType.USER, reason, null);

        log.info("User {} unbanned by admin {} with reason: {}", targetId, actorId, reason);
        return saved;
    }

    /**
     * Timeout a user by username suffix.
     * Wrapper method for moderation endpoints that use suffix-based identification.
     *
     * @param actorId        the moderator/admin user ID
     * @param usernameSuffix the username suffix of the user to timeout
     * @param durationMinutes timeout duration
     * @param reason         reason for timeout (for audit trail)
     * @return the updated user
     */
    @Transactional
    public User timeoutUserBySuffix(Long actorId, String usernameSuffix, int durationMinutes, String reason) {
        User target = userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix)
                .orElseThrow(() -> new UserNotFoundException(usernameSuffix));
        return timeoutUser(actorId, target.getId(), durationMinutes, reason);
    }

    /**
     * Remove timeout from a user by username suffix.
     * Wrapper method for moderation endpoints that use suffix-based identification.
     *
     * @param actorId        the moderator/admin user ID
     * @param usernameSuffix the username suffix of the user to clear timeout
     * @param reason         reason for clearing timeout (for audit trail)
     * @return the updated user
     */
    @Transactional
    public User removeTimeoutBySuffix(Long actorId, String usernameSuffix, String reason) {
        User target = userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix)
                .orElseThrow(() -> new UserNotFoundException(usernameSuffix));
        return removeTimeout(actorId, target.getId(), reason);
    }

    /**
     * Ban a user by username suffix.
     * Wrapper method for moderation endpoints that use suffix-based identification.
     *
     * @param actorId        the admin user ID
     * @param usernameSuffix the username suffix of the user to ban
     * @param reason         ban reason (optional)
     * @return the updated user
     */
    @Transactional
    public User banUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        User target = userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix)
                .orElseThrow(() -> new UserNotFoundException(usernameSuffix));
        return banUser(actorId, target.getId(), reason);
    }

    /**
     * Unban a user by username suffix.
     * Wrapper method for moderation endpoints that use suffix-based identification.
     *
     * @param actorId        the admin user ID
     * @param usernameSuffix the username suffix of the user to unban
     * @param reason         reason for unbanning (for audit trail)
     * @return the updated user
     */
    @Transactional
    public User unbanUserBySuffix(Long actorId, String usernameSuffix, String reason) {
        User target = userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix)
                .orElseThrow(() -> new UserNotFoundException(usernameSuffix));
        return unbanUser(actorId, target.getId(), reason);
    }

    /**
     * Take down a planner (moderator deletion).
     * Owner can still sync their local copy, but planner is removed from public.
     *
     * @param actorId   the moderator/admin performing the action
     * @param plannerId the planner to take down
     * @param reason    reason for takedown (optional)
     * @return the updated planner
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public Planner deletePlanner(Long actorId, UUID plannerId, String reason) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        planner.setTakenDownAt(Instant.now());
        planner.setPublished(false); // Also unpublish
        Planner saved = plannerRepository.save(planner);

        // Log to audit trail
        logModerationAction(actorId, plannerId.toString(),
                ModerationAction.ActionType.DELETE_PLANNER, ModerationAction.TargetType.PLANNER, reason, null);

        log.info("Planner {} taken down by moderator {} with reason: {}", plannerId, actorId, reason);
        return saved;
    }

    /**
     * Log a moderation action to the audit trail.
     */
    private void logModerationAction(Long actorId, String targetUuid, ModerationAction.ActionType actionType,
                                      ModerationAction.TargetType targetType, String reason, Integer durationMinutes) {
        ModerationAction action = ModerationAction.builder()
                .actorId(actorId)
                .targetUuid(targetUuid)
                .actionType(actionType)
                .targetType(targetType)
                .reason(reason)
                .durationMinutes(durationMinutes)
                .build();
        moderationActionRepository.save(action);
    }

    /**
     * Unpublish a planner.
     * Sets the published flag to false regardless of current state.
     *
     * @param actorId   the moderator/admin performing the action
     * @param plannerId the planner to unpublish
     * @return the updated planner
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public Planner unpublishPlanner(Long actorId, UUID plannerId) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        planner.setPublished(false);
        Planner saved = plannerRepository.save(planner);

        log.info("Planner {} unpublished by moderator {}", plannerId, actorId);
        return saved;
    }

    /**
     * Get all users for moderation dashboard.
     * Excludes soft-deleted users.
     *
     * @return list of all active users
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getDeletedAt() == null)
                .filter(u -> u.getId() != 0) // Exclude sentinel/system user
                .toList();
    }

    /**
     * Get all moderation actions for audit trail.
     * Returns most recent 100 actions.
     *
     * @return list of moderation actions
     */
    @Transactional(readOnly = true)
    public List<ModerationAction> getModerationActions() {
        return moderationActionRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(100)
                .toList();
    }

    /**
     * Get moderation actions with actor information for API response.
     * Batch-fetches all actors to avoid N+1 queries.
     * Returns most recent 100 actions with actor usernames (no internal IDs exposed).
     *
     * @return list of moderation action DTOs with actor information
     */
    @Transactional(readOnly = true)
    public List<org.danteplanner.backend.dto.moderation.ModerationActionDto> getModerationActionsWithActors() {
        List<ModerationAction> actions = getModerationActions();

        // Batch fetch all actors to avoid N+1 queries
        List<Long> actorIds = actions.stream()
                .map(ModerationAction::getActorId)
                .distinct()
                .toList();

        Map<Long, User> actorMap = userRepository.findAllById(actorIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));

        // Map to DTOs with actor information
        return actions.stream()
                .map(action -> {
                    User actor = actorMap.get(action.getActorId());
                    String epithet = actor != null ? actor.getUsernameEpithet() : "Unknown";
                    String suffix = actor != null ? actor.getUsernameSuffix() : "";
                    return org.danteplanner.backend.dto.moderation.ModerationActionDto.fromEntity(action, epithet, suffix);
                })
                .toList();
    }

    /**
     * Get all currently timed-out users.
     * Uses the V014 partial index on timeout_until for efficient lookup.
     *
     * @return list of users with active timeouts
     */
    @Transactional(readOnly = true)
    public List<User> getTimedOutUsers() {
        return userRepository.findByTimeoutUntilAfterAndDeletedAtIsNull(Instant.now());
    }

    /**
     * Soft-delete any comment as a moderator.
     *
     * @param actorId   the moderator/admin performing the action
     * @param commentId the comment ID to delete
     * @throws CommentNotFoundException if comment not found
     */
    @Transactional
    public void deleteComment(Long actorId, Long commentId) {
        PlannerComment comment = plannerCommentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        if (comment.isDeleted()) {
            // Already deleted - idempotent
            return;
        }

        comment.softDelete();
        plannerCommentRepository.save(comment);

        // Log to audit trail
        logModerationAction(actorId, comment.getPublicId().toString(),
                ModerationAction.ActionType.DELETE_COMMENT, ModerationAction.TargetType.COMMENT, null, null);

        log.info("Moderator {} deleted comment {}", actorId, commentId);
    }

    /**
     * Delete a comment by public ID (UUID).
     * Wrapper method for moderation endpoints that use public ID identification.
     * Idempotent - logs audit action even if comment already deleted.
     *
     * @param actorId         the moderator/admin user ID
     * @param commentPublicId the comment's public UUID
     * @param reason          reason for deletion (for audit trail)
     */
    @Transactional
    public void deleteCommentByPublicId(Long actorId, UUID commentPublicId, String reason) {
        PlannerComment comment = plannerCommentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

        // Log to audit trail BEFORE checking deletion status (idempotent)
        logModerationAction(actorId, comment.getPublicId().toString(),
                ModerationAction.ActionType.DELETE_COMMENT, ModerationAction.TargetType.COMMENT, reason, null);

        if (comment.isDeleted()) {
            // Already deleted - idempotent, audit logged
            log.info("Moderator {} attempted delete of already-deleted comment {} (idempotent)", actorId, commentPublicId);
            return;
        }

        comment.softDelete();
        plannerCommentRepository.save(comment);

        log.info("Moderator {} deleted comment {} with reason: {}", actorId, commentPublicId, reason);
    }

    /**
     * Hide a planner from recommended list.
     * Planner remains accessible via direct link, votes unchanged.
     *
     * @param plannerId   the planner to hide
     * @param moderatorId the moderator/admin performing the action
     * @param request     hide request with reason
     * @return moderation response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public ModerationResponse hideFromRecommended(UUID plannerId, Long moderatorId, HidePlannerRequest request) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        planner.setHiddenFromRecommended(true);
        planner.setHiddenByModeratorId(moderatorId);
        planner.setHiddenReason(request.reason());
        planner.setHiddenAt(Instant.now());

        plannerRepository.save(planner);

        log.info("Planner {} hidden from recommended by moderator {} with reason: {}",
                plannerId, moderatorId, request.reason());

        return buildModerationResponse(planner);
    }

    /**
     * Unhide a planner, restoring it to recommended list.
     *
     * @param plannerId   the planner to unhide
     * @param moderatorId the moderator/admin performing the action
     * @return moderation response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public ModerationResponse unhideFromRecommended(UUID plannerId, Long moderatorId) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        planner.setHiddenFromRecommended(false);
        planner.setHiddenByModeratorId(null);
        planner.setHiddenReason(null);
        planner.setHiddenAt(null);

        plannerRepository.save(planner);

        log.info("Planner {} unhidden from recommended by moderator {}", plannerId, moderatorId);

        return buildModerationResponse(planner);
    }

    /**
     * List all hidden planners with pagination.
     *
     * @param pageable pagination parameters
     * @return page of moderation responses
     */
    @Transactional(readOnly = true)
    public Page<ModerationResponse> listHiddenPlanners(Pageable pageable) {
        return plannerRepository.findByHiddenFromRecommendedTrueAndDeletedAtIsNull(pageable)
                .map(this::buildModerationResponse);
    }

    /**
     * Build ModerationResponse from Planner entity.
     */
    private ModerationResponse buildModerationResponse(Planner planner) {
        return new ModerationResponse(
                planner.getId(),
                planner.getTitle(),
                planner.getHiddenFromRecommended(),
                planner.getHiddenByModeratorId(),
                planner.getHiddenReason(),
                planner.getHiddenAt(),
                planner.getUpvotes()
        );
    }
}
