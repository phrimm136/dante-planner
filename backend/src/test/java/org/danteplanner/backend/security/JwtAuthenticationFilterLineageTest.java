package org.danteplanner.backend.security;

import org.danteplanner.backend.entity.AuthProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.config.LineageRotationFlag;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.danteplanner.backend.service.UserService;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private CookieUtils cookieUtils;

    @Mock
    private UserService userService;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private RefreshRotationService refreshRotationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");
    }

    private JwtAuthenticationFilter filterWithFlag(boolean lineageEnabled) {
        return new JwtAuthenticationFilter(
                tokenValidator, tokenBlacklistService, cookieUtils, userService,
                objectMapper, tokenGenerator, refreshRotationService, new LineageRotationFlag(lineageEnabled));
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
    void flagOff_usesLegacyRotation() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(false);
        String refreshToken = "legacy.refresh.jwt";
        Long userId = 42L;
        User user = activeUser(userId);

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(tokenValidator.validateToken(refreshToken)).thenReturn(refreshClaims(userId, null, null));
        when(tokenBlacklistService.isBlacklisted(refreshToken)).thenReturn(false);
        when(tokenBlacklistService.isUserTokenInvalidated(eq(userId), any(Long.class))).thenReturn(false);
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), any(), any())).thenReturn("new.access");
        when(tokenGenerator.generateRefreshToken(eq(userId), any())).thenReturn("new.refresh");

        filter.doFilterInternal(request, response, filterChain);

        verify(tokenBlacklistService).blacklistTokenForRotation(eq(refreshToken), any());
        verifyNoInteractions(refreshRotationService);
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Flag on: UNUSED_LATEST rotates, sets access cookie, authenticates")
    void flagOn_rotatedSucceeds() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "unused.latest.jwt";
        Long userId = 7L;
        User user = activeUser(userId);
        TokenClaims successorClaims = refreshClaims(userId, "successor-jti", "fam-1");

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), eq(user.getEmail()), any(UserRole.class)))
                .thenReturn("new.access.jwt");

        filter.doFilterInternal(request, response, filterChain);

        verify(refreshRotationService).rotate(refreshToken, response);
        verify(cookieUtils).setCookie(eq(response), eq(CookieConstants.ACCESS_TOKEN), eq("new.access.jwt"), anyInt());
        verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Flag on: USED token (theft) revokes family, clears context, no auth")
    void flagOn_usedTokenRevoked() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "used.jwt";

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Revoked("fam-theft"));

        filter.doFilterInternal(request, response, filterChain);

        verify(refreshRotationService).rotate(refreshToken, response);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("Flag on: SUPERSEDED token (theft) revokes family, no auth")
    void flagOn_supersededTokenRevoked() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "superseded.jwt";

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Revoked("fam-superseded"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("Flag on: revoked-family token rejected at rotation, no auth")
    void flagOn_revokedFamilyRejected() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "revoked.family.jwt";

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY));

        filter.doFilterInternal(request, response, filterChain);

        verify(refreshRotationService).rotate(refreshToken, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("Flag on + legacy-admit on: legacy token admitted, sets cookie, authenticates")
    void flagOn_legacyAdmitted() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "legacy.refresh.jwt";
        Long userId = 99L;
        User user = activeUser(userId);
        TokenClaims successorClaims = refreshClaims(userId, "synth-successor-jti", "synth-fam");

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), eq(user.getEmail()), any(UserRole.class)))
                .thenReturn("new.access.jwt");

        filter.doFilterInternal(request, response, filterChain);

        verify(refreshRotationService).rotate(refreshToken, response);
        verify(cookieUtils).setCookie(eq(response), eq(CookieConstants.ACCESS_TOKEN), eq("new.access.jwt"), anyInt());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Flag on + legacy-admit off: legacy token rejected, no auth, no rotation")
    void flagOn_legacyRejectedWhenAdmitDisabled() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String refreshToken = "legacy.refresh.jwt";

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID));

        filter.doFilterInternal(request, response, filterChain);

        verify(refreshRotationService).rotate(refreshToken, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenGenerator, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("Flag on: expired access token triggers lineage refresh path")
    void flagOn_expiredAccessTriggersLineageRefresh() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        String accessToken = "expired.access.jwt";
        String refreshToken = "unused.latest.jwt";
        Long userId = 11L;
        User user = activeUser(userId);
        TokenClaims successorClaims = refreshClaims(userId, "successor-jti", "fam-2");

        when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(accessToken);
        when(tokenValidator.validateToken(accessToken))
                .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
        when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
        when(refreshRotationService.rotate(eq(refreshToken), eq(response)))
                .thenReturn(new RotationResult.Rotated("new.refresh.jwt", successorClaims));
        when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
        when(tokenGenerator.generateAccessToken(eq(userId), eq(user.getEmail()), any(UserRole.class)))
                .thenReturn("new.access.jwt");

        filter.doFilterInternal(request, response, filterChain);

        verify(refreshRotationService).rotate(refreshToken, response);
        verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
