package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.TestJwt;
import java.util.List;
import org.danteplanner.backend.user.api.SessionEndHandler.SessionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionRevocationService} logout with {@code jwt.rotation.lineage-enabled} turned on.
 *
 * <p>Logout revokes the current refresh-token family and still blacklists the access token
 * immediately, so neither half of the pair outlives the session.</p>
 */
class SessionRevocationServiceLineageTest {

    private static final Long USER_ID = 123L;

    private final TestJwt jwt = new TestJwt();

    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(jwt.clock());

    private SessionRevocationService facade;

    @BeforeEach
    void setUp() {
        facade = new SessionRevocationService(jwt.service(), tokenBlacklist, new LineageRotationFlag(true));
    }

    @Test
    void logout_WhenFlagOn_RevokesFamily() {
        String accessToken = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);
        String refreshToken = jwt.service().generateRefreshToken(USER_ID);

        facade.logout(List.of(new SessionToken.Access(accessToken), new SessionToken.Refresh(refreshToken)));

        assertThat(tokenBlacklist.revocations()).containsExactly(
                new LogoutRevocation.TokenRevocation(accessToken,
                        jwt.service().validateAccessToken(accessToken).expiration()),
                new LogoutRevocation.TokenRevocation(refreshToken,
                        jwt.service().validateRefreshToken(refreshToken).expiration()),
                new LogoutRevocation.FamilyRevocation(TestJwt.familyIdOf(jwt.service().validateRefreshToken(refreshToken))));
    }

    @Test
    void logout_WhenFlagOn_BlacklistsAccessToken() {
        String accessToken = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);

        facade.logout(List.of(new SessionToken.Access(accessToken)));

        assertThat(tokenBlacklist.revocations()).containsExactly(
                new LogoutRevocation.TokenRevocation(accessToken,
                        jwt.service().validateAccessToken(accessToken).expiration()));
        assertThat(tokenBlacklist.blacklistedTokens()).containsExactly(accessToken);
    }
}
