package org.danteplanner.backend.admin.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.api.RoleChange;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.danteplanner.backend.moderation.api.ModerationAudit;

/**
 * Service for administrative operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final Users users;
    private final ModerationAudit auditService;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UserSummary changeRole(Long actorId, Long targetId, UserRole newRole) {
        RoleChange change = users.changeRole(actorId, targetId, newRole);
        UserRole oldRole = change.previousRole();
        UserSummary updated = change.user();

        boolean demotion = oldRole.outranks(newRole);
        auditService.record(actorId, updated.publicId().toString(),
                demotion ? ModerationActionType.DEMOTE : ModerationActionType.PROMOTE,
                ModerationTargetType.USER, oldRole + " -> " + newRole);

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
