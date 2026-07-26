package org.danteplanner.backend.shared.security;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.danteplanner.backend.shared.config.JwtProperties;

/**
 * Integration tests for {@link JwtAuthenticationFilter} lineage-rotation behavior.
 *
 * <p>Covers both flag states: flag off must behave identically to the legacy
 * blacklist-on-rotation path (delegating to {@link TokenBlacklistService}), and
 * flag on must delegate auto-refresh to {@link RefreshRotationService}.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterLineageTest {

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserService userService;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private RefreshRotationService refreshRotationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");
    private final JwtProperties jwtProperties = new JwtProperties();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", "/test");
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @AfterEach
    void clearAuthentication() {
        // SecurityContextHolder's default strategy is a ThreadLocal that outlives this class, and
        // MockMvc elsewhere runs its filter chain on the same thread; a leftover authentication
        // makes a later class's request run as this test's user.
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filterWithFlag(boolean lineageEnabled) {
        return new JwtAuthenticationFilter(
                tokenValidator, tokenBlacklistService, cookieUtils, userService,
                objectMapper, tokenGenerator, refreshRotationService, new LineageRotationFlag(lineageEnabled),
                jwtProperties);
    }

    private TokenClaims refreshClaims(Long userId, String jti, String familyId) {
        return new TokenClaims(
                userId, "user@example.com", TokenClaims.TYPE_REFRESH, null,
                new Date(), new Date(System.currentTimeMillis() + 604800000L),
                jti, familyId, null);
    }

    private User activeUser(Long id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + id)
                .usernameEpithet("W_CORP")
                .usernameSuffix("usr" + String.format("%02d", id % 100))
                .build();
    }

    @Test
    @DisplayName("Flag off: auto-refresh uses legacy blacklist rotation, never the rotation service")
    void doFilterInternal_WhenFlagOff_UsesLegacyRotation() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(false);
        String refreshToken = "legacy.refresh.jwt";
        Long userId = 42L;
        User user = activeUser(userId);

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refreshClaims(userId, null, null));
        when(tokenBlacklistService.isBlacklisted(refreshToken)).thenReturn(false);
        when(tokenBlacklistService.isUserTokenInvalidated(eq(userId), any(Long.class))).thenReturn(false);
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), any())).thenReturn("new.access");
        when(tokenGenerator.generateRefreshToken(eq(userId))).thenReturn("new.refresh");

        filter.doFilterInternal(request, response, filterChain);

        // Minting both cookies is what distinguishes the legacy path: the lineage path
        // leaves the refresh cookie to RefreshRotationService and sets only the access cookie.
        Cookie accessCookie = response.getCookie(CookieConstants.ACCESS_TOKEN);
        assertNotNull(accessCookie);
        assertEquals("new.access", accessCookie.getValue());
        assertEquals(jwtProperties.getAccessTokenExpirySeconds(), accessCookie.getMaxAge());
        Cookie refreshCookie = response.getCookie(CookieConstants.REFRESH_TOKEN);
        assertNotNull(refreshCookie);
        assertEquals("new.refresh", refreshCookie.getValue());
        assertEquals(jwtProperties.getRefreshTokenExpirySeconds(), refreshCookie.getMaxAge());
        // The rotation blacklist entry lands in Redis and has no response-visible form;
        // asserting it as state needs the containerized tier with a live TokenBlacklistService.
        verify(tokenBlacklistService).blacklistTokenForRotation(eq(refreshToken), any());
        verifyNoInteractions(refreshRotationService);
        assertSame(request, filterChain.getRequest());
        assertSame(response, filterChain.getResponse());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("Flag on: UNUSED_LATEST rotates, sets access cookie, authenticates")
    void doFilterInternal_WhenFlagOnAndUnusedLatest_RotatesAndAuthenticates() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "unused.latest.jwt";
        Long userId = 7L;
        User user = activeUser(userId);
        TokenClaims successorClaims = refreshClaims(userId, "successor-jti", "fam-1");

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), any(UserRole.class)))
                .thenReturn("new.access.jwt");

        filter.doFilterInternal(request, response, filterChain);

        Cookie accessCookie = response.getCookie(CookieConstants.ACCESS_TOKEN);
        assertNotNull(accessCookie);
        assertEquals("new.access.jwt", accessCookie.getValue());
        assertEquals(jwtProperties.getAccessTokenExpirySeconds(), accessCookie.getMaxAge());
        verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("Flag on: RETIRED token (theft) revokes family, clears context, no auth")
    void doFilterInternal_WhenFlagOnAndUsedToken_RevokesFamilyNoAuth() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "used.jwt";

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Revoked("fam-theft"));

        filter.doFilterInternal(request, response, filterChain);

        // attemptAutoRefresh swallows every exception, so a stub-argument mismatch cannot fail
        // the run: only this verify pins that the presented token reached the rotation seam.
        verify(refreshRotationService).rotate(refreshToken, response);
        assertNull(response.getCookie(CookieConstants.ACCESS_TOKEN));
        assertSame(request, filterChain.getRequest());
        assertSame(response, filterChain.getResponse());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Flag on: SUPERSEDED token (theft) revokes family, no auth")
    void doFilterInternal_WhenFlagOnAndSupersededToken_RevokesFamilyNoAuth() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "superseded.jwt";

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Revoked("fam-superseded"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Flag on: revoked-family token rejected at rotation, no auth")
    void doFilterInternal_WhenFlagOnAndRevokedFamily_RejectedNoAuth() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "revoked.family.jwt";

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY));

        filter.doFilterInternal(request, response, filterChain);

        // attemptAutoRefresh swallows every exception, so a stub-argument mismatch cannot fail
        // the run: only this verify pins that the presented token reached the rotation seam.
        verify(refreshRotationService).rotate(refreshToken, response);
        assertNull(response.getCookie(CookieConstants.ACCESS_TOKEN));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Flag on + legacy-admit on: legacy token admitted, sets cookie, authenticates")
    void doFilterInternal_WhenFlagOnAndLegacyAdmit_AdmitsAndAuthenticates() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "legacy.refresh.jwt";
        Long userId = 99L;
        User user = activeUser(userId);
        TokenClaims successorClaims = refreshClaims(userId, "synth-successor-jti", "synth-fam");

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), any(UserRole.class)))
                .thenReturn("new.access.jwt");

        filter.doFilterInternal(request, response, filterChain);

        Cookie accessCookie = response.getCookie(CookieConstants.ACCESS_TOKEN);
        assertNotNull(accessCookie);
        assertEquals("new.access.jwt", accessCookie.getValue());
        assertEquals(jwtProperties.getAccessTokenExpirySeconds(), accessCookie.getMaxAge());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("Flag on + legacy-admit off: legacy token rejected, no auth, no rotation")
    void doFilterInternal_WhenFlagOnAndLegacyAdmitOff_RejectedNoAuth() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "legacy.refresh.jwt";

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID));

        filter.doFilterInternal(request, response, filterChain);

        // attemptAutoRefresh swallows every exception, so a stub-argument mismatch cannot fail
        // the run: only this verify pins that the presented token reached the rotation seam.
        verify(refreshRotationService).rotate(refreshToken, response);
        assertNull(response.getCookie(CookieConstants.ACCESS_TOKEN));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("Flag on: expired access token triggers lineage refresh path")
    void doFilterInternal_WhenFlagOnAndExpiredAccess_TriggersLineageRefresh() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String accessToken = "expired.access.jwt";
        String refreshToken = "unused.latest.jwt";
        Long userId = 11L;
        User user = activeUser(userId);
        TokenClaims successorClaims = refreshClaims(userId, "successor-jti", "fam-2");

        request.setCookies(
                new Cookie(CookieConstants.ACCESS_TOKEN, accessToken),
                new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        when(tokenValidator.validateAccessToken(accessToken))
                .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), any(UserRole.class)))
                .thenReturn("new.access.jwt");

        filter.doFilterInternal(request, response, filterChain);

        Cookie accessCookie = response.getCookie(CookieConstants.ACCESS_TOKEN);
        assertNotNull(accessCookie);
        assertEquals("new.access.jwt", accessCookie.getValue());
        assertEquals(jwtProperties.getAccessTokenExpirySeconds(), accessCookie.getMaxAge());
        verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
