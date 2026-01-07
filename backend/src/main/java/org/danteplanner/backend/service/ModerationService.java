package org.danteplanner.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    public User timeoutUser(Long actorId, Long targetId, int durationMinutes) {
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

        log.info("User {} timed out until {} by moderator {}", targetId, timeoutUntil, actorId);
        return saved;
    }

    /**
     * Remove timeout from a user.
     *
     * @param actorId  the moderator/admin performing the action
     * @param targetId the user to remove timeout from
     * @return the updated user
     * @throws UserNotFoundException if target user not found
     */
    @Transactional
    public User removeTimeout(Long actorId, Long targetId) {
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        target.setTimeoutUntil(null);
        User saved = userRepository.save(target);

        log.info("Timeout removed from user {} by moderator {}", targetId, actorId);
        return saved;
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
     * Get all currently timed-out users.
     * Uses the V014 partial index on timeout_until for efficient lookup.
     *
     * @return list of users with active timeouts
     */
    @Transactional(readOnly = true)
    public List<User> getTimedOutUsers() {
        return userRepository.findByTimeoutUntilAfterAndDeletedAtIsNull(Instant.now());
    }
}
