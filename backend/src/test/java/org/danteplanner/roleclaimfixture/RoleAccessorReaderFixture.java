package org.danteplanner.roleclaimfixture;

import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.user.api.UserRole;

public class RoleAccessorReaderFixture {

    public boolean isModerator(TokenClaims claims) {
        return claims.role() == UserRole.MODERATOR;
    }
}
