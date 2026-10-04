package org.danteplanner.backend.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
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

    private final Users users;
    private final ModerationAuditService auditService;
    private final ModerationPolicy moderationPolicy;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UserSummary changeRole(Long actorId, Long targetId, UserRole newRole) {
        UserSummary actor = users.lockActiveById(actorId);
        UserSummary target = users.lockActiveById(targetId);

        UserRole targetCurrentRole = target.role();

        moderationPolicy.requireCanChangeRole(actor, target, newRole);

        if (moderationPolicy.demotesAnAdministrator(targetCurrentRole, newRole)) {
            moderationPolicy.requireAnotherAdministratorRemains(users.countByRole(UserRole.ADMIN));
        }

        UserRole oldRole = target.role();
        UserSummary updated = users.changeRole(targetId, newRole);

        boolean demotion = oldRole.outranks(newRole);
        auditService.record(actorId, target.publicId().toString(),
                demotion ? ModerationAction.ActionType.DEMOTE : ModerationAction.ActionType.PROMOTE,
                ModerationAction.TargetType.USER, oldRole + " -> " + newRole);

        if (demotion) {
            log.info("User {} demoted from {} to {} by admin {}", targetId, oldRole, newRole, actorId);
        } else {
            log.info("User {} role changed from {} to {} by admin {}",
                    targetId, oldRole, newRole, actorId);
        }

        return updated;
    }

    @Transactional(readOnly = true)
    public UserRole getUserRole(Long userId) {
        UserSummary user = users.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return user.role();
    }
}
