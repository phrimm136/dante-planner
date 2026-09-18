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
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.moderation.service.ModerationPolicy;

/**
 * Service for administrative operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModerationAuditService auditService;
    private final ModerationPolicy moderationPolicy;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public User changeRole(Long actorId, Long targetId, UserRole newRole) {
        User actor = userService.lockActiveById(actorId);
        User target = userService.lockActiveById(targetId);

        UserRole targetCurrentRole = target.getRole();

        moderationPolicy.requireCanChangeRole(actor, target, newRole);

        if (moderationPolicy.demotesAnAdministrator(targetCurrentRole, newRole)) {
            moderationPolicy.requireAnotherAdministratorRemains(userService.countByRole(UserRole.ADMIN));
        }

        UserRole oldRole = target.getRole();
        target.setRole(newRole);

        boolean demotion = oldRole.outranks(newRole);
        auditService.record(actorId, target.getPublicId().toString(),
                demotion ? ModerationAction.ActionType.DEMOTE : ModerationAction.ActionType.PROMOTE,
                ModerationAction.TargetType.USER, oldRole + " -> " + newRole);

        if (demotion) {
            eventPublisher.publishEvent(new UserDemotedEvent(this, targetId, oldRole, newRole));
            log.info("User {} demoted from {} to {} by admin {}", targetId, oldRole, newRole, actorId);
        } else {
            log.info("User {} role changed from {} to {} by admin {}",
                    targetId, oldRole, newRole, actorId);
        }

        return target;
    }

    @Transactional(readOnly = true)
    public UserRole getUserRole(Long userId) {
        User user = userService.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return user.getRole();
    }
}
