package org.danteplanner.backend.facade;

import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.config.LineageRotationFlag;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.SessionRevokedException;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.service.token.RefreshRotationService;
import org.danteplanner.backend.service.token.RotationResult;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.danteplanner.backend.service.token.TokenClaims;
import org.danteplanner.backend.service.token.TokenGenerator;
import org.danteplanner.backend.service.token.TokenValidator;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link AuthenticationFacade} lineage-rotation behavior
 * with the {@code jwt.rotation.lineage-enabled} flag turned on.
 *
 * <p>Verifies that {@code refreshTokens} delegates to {@link RefreshRotationService}
 * and that {@code logout} revokes the current refresh-token family while still
 * blacklisting the access token immediately.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFacadeLineageTest {

    @Mock
    private OAuthProviderRegistry providerRegistry;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserService userService;

    @Mock
    private UserAccountLifecycleService lifecycleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshRotationService refreshRotationService;

    @Mock
    private CookieUtils cookieUtils;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private HttpServletResponse response;

    private AuthenticationFacade facade;
    private User testUser;

    @BeforeEach
    void setUp() {
        facade = new AuthenticationFacade(
                providerRegistry, tokenGenerator, tokenValidator, tokenBlacklistService,
                userService, lifecycleService, userRepository, refreshRotationService,
                cookieUtils, jwtProperties, new LineageRotationFlag(true));

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("tst01")
                .build();
    }

    private TokenClaims refreshClaims(String jti, String familyId, String parentJti) {
        return new TokenClaims(
                testUser.getId(), testUser.getEmail(), TokenClaims.TYPE_REFRESH, null,
                new Date(), new Date(System.currentTimeMillis() + 604800000L),
                jti, familyId, parentJti);
    }

    @Test
    @DisplayName("refreshTokens produces lineage claims and access token via rotation when flag on")
    void refreshTokens_flagOn_delegatesToRotation() {
        String oldRefresh = "old.refresh.jwt";
        TokenClaims successorClaims = refreshClaims("successor-jti", "fam-1", "parent-jti");

        when(refreshRotationService.rotate(eq(oldRefresh), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findById(testUser.getId())).thenReturn(testUser);
        when(tokenGenerator.generateAccessToken(eq(testUser.getId()), eq(testUser.getEmail()), any(UserRole.class)))
                .thenReturn("new.access.jwt");
        when(jwtProperties.getCookieExpirySeconds()).thenReturn(604800);

        AuthenticationFacade.AuthResult result = facade.refreshTokens(oldRefresh, response);

        assertSame(testUser, result.user());
        assertEquals("new.access.jwt", result.accessToken());
        assertEquals("new.refresh.jwt", result.refreshToken());
        verify(refreshRotationService).rotate(oldRefresh, response);
        verify(cookieUtils).setCookie(eq(response), eq(CookieConstants.ACCESS_TOKEN), eq("new.access.jwt"), anyInt());
        verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
    }

    @Test
    @DisplayName("refreshTokens throws SessionRevokedException on revoked family when flag on")
    void refreshTokens_flagOn_revokedFamilyThrows() {
        String stolen = "stolen.refresh.jwt";
        when(refreshRotationService.rotate(eq(stolen), eq(response)))
                .thenReturn(new RotationResult.Revoked("fam-theft"));

        SessionRevokedException ex = assertThrows(SessionRevokedException.class,
                () -> facade.refreshTokens(stolen, response));
        assertEquals("fam-theft", ex.getFamilyId());
        verify(tokenGenerator, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("logout revokes current family when flag on; subsequent refresh in same family is revoked")
    void logout_flagOn_revokesFamily() {
        String accessToken = "access.jwt";
        String refreshToken = "refresh.jwt";
        Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
        TokenClaims accessClaims = new TokenClaims(
                testUser.getId(), testUser.getEmail(), TokenClaims.TYPE_ACCESS, UserRole.NORMAL,
                new Date(), accessExpiry);
        TokenClaims refresh = refreshClaims("jti-1", "fam-logout", null);

        when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);
        when(tokenValidator.validateToken(refreshToken)).thenReturn(refresh);

        facade.logout(accessToken, refreshToken);

        verify(tokenBlacklistService).blacklistToken(accessToken, accessExpiry);
        verify(refreshRotationService).revokeFamily("fam-logout");
    }

    @Test
    @DisplayName("logout still blacklists access token immediately when flag on")
    void logout_flagOn_blacklistsAccessToken() {
        String accessToken = "access.jwt";
        Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
        TokenClaims accessClaims = new TokenClaims(
                testUser.getId(), testUser.getEmail(), TokenClaims.TYPE_ACCESS, UserRole.NORMAL,
                new Date(), accessExpiry);

        when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);

        facade.logout(accessToken, null);

        verify(tokenBlacklistService).blacklistToken(accessToken, accessExpiry);
        verify(refreshRotationService, never()).revokeFamily(any());
    }
}
