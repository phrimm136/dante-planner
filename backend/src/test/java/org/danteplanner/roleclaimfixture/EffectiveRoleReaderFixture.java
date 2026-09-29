package org.danteplanner.roleclaimfixture;

import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.user.entity.UserRole;

public class EffectiveRoleReaderFixture {

    public boolean isModerator(TokenClaims claims) {
        return claims.getEffectiveRole() == UserRole.MODERATOR;
    }
}
