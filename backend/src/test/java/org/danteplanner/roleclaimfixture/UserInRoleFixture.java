package org.danteplanner.roleclaimfixture;

import jakarta.servlet.http.HttpServletRequest;

public class UserInRoleFixture {

    public boolean isModerator(HttpServletRequest request) {
        return request.isUserInRole("MODERATOR");
    }
}
