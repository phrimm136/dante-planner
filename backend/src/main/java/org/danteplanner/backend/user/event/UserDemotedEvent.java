package org.danteplanner.backend.user.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import org.danteplanner.backend.user.entity.UserRole;

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
