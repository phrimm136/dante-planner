package org.danteplanner.fixture.roleclaim;

import jakarta.servlet.http.HttpServletRequest;

public class UserInRoleFixture {

    public boolean isModerator(HttpServletRequest request) {
        return request.isUserInRole("MODERATOR");
    }
}
