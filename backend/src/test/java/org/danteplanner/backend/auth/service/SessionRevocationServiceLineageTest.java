package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SessionRevocationService} logout with {@code jwt.rotation.lineage-enabled} turned on.
 *
 * <p>Logout revokes the current refresh-token family and still blacklists the access token
 * immediately, so neither half of the pair outlives the session.</p>
 */
@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceLineageTest {

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private SessionRevocationService facade;
    private User testUser;

    @BeforeEach
    void setUp() {
        facade = new SessionRevocationService(tokenValidator, tokenBlacklistService, new LineageRotationFlag(true));

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("tst01")
                .build();
    }

    private TokenClaims refreshClaims(String jti, String familyId, String parentJti) {
        return new TokenClaims(
                testUser.getId(), TokenClaims.TYPE_REFRESH, null,
                new Date(), new Date(System.currentTimeMillis() + 604800000L),
                jti, familyId, parentJti);
    }



    @Test
    @DisplayName("logout revokes current family when flag on; subsequent refresh in same family is revoked")
    void logout_WhenFlagOn_RevokesFamily() {
        String accessToken = "access.jwt";
        String refreshToken = "refresh.jwt";
        Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
        TokenClaims accessClaims = new TokenClaims(
                testUser.getId(), TokenClaims.TYPE_ACCESS, UserRole.NORMAL,
                new Date(), accessExpiry);
        TokenClaims refresh = refreshClaims("jti-1", "fam-logout", null);

        when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);
        when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refresh);

        facade.logout(accessToken, refreshToken);

        // The logout revocation lands in Redis and has no response-visible form;
        // asserting it as state needs the containerized tier with a live TokenBlacklistService.
        verify(tokenBlacklistService).revokeLogoutSession(List.of(
                new LogoutRevocation.TokenRevocation(accessToken, accessExpiry),
                new LogoutRevocation.TokenRevocation(refreshToken, refresh.expiration()),
                new LogoutRevocation.FamilyRevocation("fam-logout")));
    }

    @Test
    @DisplayName("logout still blacklists access token immediately when flag on")
    void logout_WhenFlagOn_BlacklistsAccessToken() {
        String accessToken = "access.jwt";
        Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
        TokenClaims accessClaims = new TokenClaims(
                testUser.getId(), TokenClaims.TYPE_ACCESS, UserRole.NORMAL,
                new Date(), accessExpiry);

        when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);

        facade.logout(accessToken, null);

        // The access-token blacklist entry lands in Redis and has no response-visible form;
        // asserting it as state needs the containerized tier with a live TokenBlacklistService.
        verify(tokenBlacklistService).revokeLogoutSession(List.of(
                new LogoutRevocation.TokenRevocation(accessToken, accessExpiry)));
    }
}
