package org.danteplanner.backend.moderation.api;

import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;

public interface RoleChangePolicy {

    boolean demotesAnAdministrator(UserRole currentRole, UserRole newRole);

    void requireAnotherAdministratorRemains(long administratorCount);

    void requireCanChangeRole(UserSummary actor, UserSummary target, UserRole newRole);
}
