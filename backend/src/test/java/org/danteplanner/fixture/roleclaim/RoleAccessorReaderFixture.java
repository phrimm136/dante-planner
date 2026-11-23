package org.danteplanner.fixture.roleclaim;

import org.danteplanner.backend.auth.token.AccessClaims;
import org.danteplanner.backend.user.api.UserRole;

public class RoleAccessorReaderFixture {

    public boolean isModerator(AccessClaims claims) {
        return claims.role() == UserRole.MODERATOR;
    }
}
