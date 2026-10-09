package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;

@Service
public class ModerationPolicy {

    private record Authority(UserRole minimumActorRole, String power, String verb) {}

    private static final Map<ModerationActionType, Authority> AUTHORITIES = Map.of(
            ModerationActionType.TIMEOUT,
            new Authority(UserRole.MODERATOR, "timeout users", "timeout"),
            ModerationActionType.CLEAR_TIMEOUT,
            new Authority(UserRole.MODERATOR, "clear timeouts", "clear the timeout of"),
            ModerationActionType.BAN,
            new Authority(UserRole.ADMIN, "ban users", "ban"),
            ModerationActionType.UNBAN,
            new Authority(UserRole.ADMIN, "unban users", "unban"));

    private static final Map<UserRole, String> ROLE_PLURAL = Map.of(
            UserRole.NORMAL, "users",
            UserRole.MODERATOR, "moderators",
            UserRole.ADMIN, "administrators");

    public void requireCanRestrict(User actor, User target, ModerationActionType action) {
        Authority authority = AUTHORITIES.get(action);
        Assert.notNull(authority, () -> "Not an account restriction: " + action);

        if (!actor.getRole().hasRankAtLeast(authority.minimumActorRole())) {
            throw new ModerationForbiddenException("Only %s can %s"
                    .formatted(ROLE_PLURAL.get(authority.minimumActorRole()), authority.power()));
        }

        if (!actor.getRole().outranks(target.getRole())) {
            throw new ModerationForbiddenException(
                    "Cannot %s a user of equal or higher rank".formatted(authority.verb()));
        }
    }
}
