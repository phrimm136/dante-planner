package org.danteplanner.backend.user.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import org.danteplanner.backend.user.entity.UserRole;

/**
 * Published when an account's role is lowered, so credentials issued under the old role can be
 * withdrawn.
 *
 * <p>Carried rather than acted on inline because the withdrawal is a write to the auth store, and
 * a role change holds row and gap locks while it runs. Delivered AFTER_COMMIT: an invalidation
 * raised by a demotion that then rolls back would log out a user whose role never changed.</p>
 *
 * <p>Lives in {@code user} because both the feature that raises it and the feature that acts on it
 * may depend on {@code user}, while they may not depend on each other.</p>
 *
 * @see org.danteplanner.backend.auth.listener.UserDemotionListener
 */
@Getter
public class UserDemotedEvent extends ApplicationEvent {

    private final Long userId;
    private final UserRole previousRole;
    private final UserRole newRole;

    public UserDemotedEvent(Object source, Long userId, UserRole previousRole, UserRole newRole) {
        super(source);
        this.userId = userId;
        this.previousRole = previousRole;
        this.newRole = newRole;
    }
}
