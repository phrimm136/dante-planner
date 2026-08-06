package org.danteplanner.backend.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.event.UserDemotedEvent;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.moderation.service.ModerationPolicy;

/**
 * Service for administrative operations.
 * Only ADMIN users can perform these operations (enforced at controller level).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModerationAuditService auditService;
    private final ModerationPolicy moderationPolicy;

    /**
     * Change a user's role with safeguards.
     *
     * @param actorId   the admin performing the action
     * @param targetId  the user whose role is being changed
     * @param newRole   the new role to assign
     * @return the updated user
     * @throws UserNotFoundException        if target user not found
     * @throws ModerationForbiddenException if a rank safeguard rejects the change
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public User changeRole(Long actorId, Long targetId, UserRole newRole) {
        // Use pessimistic locking to prevent TOCTOU race conditions
        User actor = userService.lockActiveById(actorId);
        User target = userService.lockActiveById(targetId);

        UserRole targetCurrentRole = target.getRole();

        moderationPolicy.requireCanChangeRole(actor, target, newRole);

        if (targetCurrentRole == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long adminCount = userService.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new ModerationForbiddenException("Cannot demote the last administrator");
            }
        }

        // Apply role change
        UserRole oldRole = target.getRole();
        target.setRole(newRole);
        User saved = userService.save(target);

        boolean demotion = oldRole.outranks(newRole);
        auditService.record(actorId, target.getPublicId().toString(),
                demotion ? ModerationAction.ActionType.DEMOTE : ModerationAction.ActionType.PROMOTE,
                ModerationAction.TargetType.USER, oldRole + " -> " + newRole, null);

        // Credentials issued under the old role are withdrawn after this commits
        if (demotion) {
            eventPublisher.publishEvent(new UserDemotedEvent(this, targetId, oldRole, newRole));
            log.info("User {} demoted from {} to {} by admin {}", targetId, oldRole, newRole, actorId);
        } else {
            log.info("User {} role changed from {} to {} by admin {}",
                    targetId, oldRole, newRole, actorId);
        }

        return saved;
    }

    /**
     * Get a user's current role.
     *
     * @param userId the user ID
     * @return the user's role
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserRole getUserRole(Long userId) {
        User user = userService.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return user.getRole();
    }
}
