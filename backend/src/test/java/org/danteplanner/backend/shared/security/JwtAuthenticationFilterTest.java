package org.danteplanner.backend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Optional;

import org.danteplanner.backend.auth.exception.InvalidTokenException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * <p>The filter authenticates from token claims alone (no per-request DB lookup): a valid
 * token authenticates without touching {@code UserService}; deleted/demoted users are rejected
 * via the in-memory {@code isUserTokenInvalidated} check; and a DB outage during the refresh
 * path returns 503 rather than a 500 or a silent guest downgrade.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

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
    private org.danteplanner.backend.auth.token.RefreshRotationService refreshRotationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new JwtAuthenticationFilter(tokenValidator, tokenBlacklistService, cookieUtils, userService, objectMapper, tokenGenerator, refreshRotationService, new LineageRotationFlag(false));
        SecurityContextHolder.clearContext();
        // doFilterInternal reads method+path at the top for MDC — stub to avoid NPE
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");
    }

    private TokenClaims createValidClaims(Long userId) {
        return new TokenClaims(
                userId,
                "test@example.com",
                TokenClaims.TYPE_ACCESS,
                UserRole.NORMAL,
                new Date(),
                new Date(System.currentTimeMillis() + 3600000)
        );
    }

    private TokenClaims createRefreshClaims(Long userId) {
        return new TokenClaims(
                userId,
                "test@example.com",
                TokenClaims.TYPE_REFRESH,
                UserRole.NORMAL,
                new Date(),
                new Date(System.currentTimeMillis() + 3600000)
        );
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

    @Nested
    @DisplayName("Token-only Authentication Tests (Fix 3)")
    class ActiveUserAuthenticationTests {

        @Test
        @DisplayName("Valid token authenticates from claims WITHOUT any DB lookup")
        void doFilterInternal_validToken_authenticatesWithoutDbLookup() throws Exception {
            String token = "valid.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            // The hot path must not touch the DB (keeps auth alive during DB maintenance)
            verify(userService, never()).findActiveById(any());
            verify(userService, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("Invalidated User Tests (deleted/demoted via in-memory invalidation)")
    class InvalidatedUserTests {

        @Test
        @DisplayName("Token issued before invalidation is rejected (deleted/demoted user)")
        void doFilterInternal_invalidatedUser_clearsContextAndContinues() throws Exception {
            String token = "valid.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            // deleteAccount()/demotion called invalidateUserTokens → this returns true
            when(tokenBlacklistService.isUserTokenInvalidated(eq(userId), anyLong())).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "Invalidated user must not be authenticated");
            verify(userService, never()).findActiveById(any());
        }
    }

    @Nested
    @DisplayName("Sentinel User Protection Tests")
    class SentinelUserProtectionTests {

        @Test
        @DisplayName("Should skip authentication for sentinel user (id=0)")
        void doFilterInternal_sentinelUser_skipsAuthentication() throws Exception {
            String token = "sentinel.jwt.token";
            Long sentinelId = 0L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(sentinelId));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(userService, never()).findActiveById(any());
        }
    }

    @Nested
    @DisplayName("No Token Tests")
    class NoTokenTests {

        @Test
        @DisplayName("Should continue without authentication when no token present")
        void doFilterInternal_noToken_continuesWithoutAuth() throws Exception {
            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(tokenValidator, never()).validateToken(any());
        }
    }

    @Nested
    @DisplayName("DB Unavailable During Refresh Tests (Fix 2a)")
    class DbUnavailableDuringRefreshTests {

        @Test
        @DisplayName("Expired token + DB down during refresh returns 503, not a silent guest")
        void doFilterInternal_expiredToken_refreshDbDown_returns503() throws Exception {
            String expiredToken = "expired.jwt.token";
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(expiredToken);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
            when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            when(tokenValidator.validateToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(userService.findActiveById(userId))
                    .thenThrow(new DataAccessResourceFailureException("DB unreachable"));
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("No access token + refresh present + DB down during refresh returns 503 (Site 1)")
        void doFilterInternal_noAccessToken_refreshDbDown_returns503() throws Exception {
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            // Access cookie absent (expiry/MaxAge desync) but a refresh cookie is present —
            // this routes through the token==null branch, a different catch site than the
            // expired-token path below.
            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
            when(tokenValidator.validateToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(userService.findActiveById(userId))
                    .thenThrow(new DataAccessResourceFailureException("DB unreachable"));
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("Expired token + tx-begin failure during refresh returns 503, not a silent guest")
        void doFilterInternal_expiredToken_refreshTxBeginFails_returns503() throws Exception {
            String expiredToken = "expired.jwt.token";
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(expiredToken);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
            when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            when(tokenValidator.validateToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            // findActiveById is @Transactional; a DB-down failure surfaces at transaction-begin
            // as CannotCreateTransactionException, NOT DataAccessResourceFailureException.
            when(userService.findActiveById(userId))
                    .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("Redis auth-write failure during refresh returns 503 with AUTH_TEMPORARILY_UNAVAILABLE body")
        void doFilterInternal_refreshRedisWriteFails_returnsAuthTemporarilyUnavailable() throws Exception {
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;
            User user = activeUser(userId);
            StringWriter body = new StringWriter();

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
            when(tokenValidator.validateToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(tokenBlacklistService.isBlacklisted(refreshToken)).thenReturn(false);
            when(tokenBlacklistService.isUserTokenInvalidated(eq(userId), anyLong())).thenReturn(false);
            when(userService.findActiveById(userId)).thenReturn(Optional.of(user));
            doThrow(new org.springframework.data.redis.RedisConnectionFailureException("auth redis down"))
                    .when(tokenBlacklistService).blacklistTokenForRotation(eq(refreshToken), any());
            when(response.getWriter()).thenReturn(new PrintWriter(body));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            verify(filterChain, never()).doFilter(any(), any());
            assertTrue(body.toString().contains("AUTH_TEMPORARILY_UNAVAILABLE"),
                    "Redis auth-write failure must write AUTH_TEMPORARILY_UNAVAILABLE, got: " + body);
        }

        @Test
        @DisplayName("DB down during refresh writes body code WRITE_TEMPORARILY_UNAVAILABLE")
        void doFilterInternal_refreshDbDown_bodyCodeIsWriteTemporarilyUnavailable() throws Exception {
            String expiredToken = "expired.jwt.token";
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;
            StringWriter body = new StringWriter();

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(expiredToken);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(refreshToken);
            when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            when(tokenValidator.validateToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(userService.findActiveById(userId))
                    .thenThrow(new DataAccessResourceFailureException("DB unreachable"));
            when(response.getWriter()).thenReturn(new PrintWriter(body));

            filter.doFilterInternal(request, response, filterChain);

            assertTrue(body.toString().contains("WRITE_TEMPORARILY_UNAVAILABLE"),
                    "DB-down refresh must write WRITE_TEMPORARILY_UNAVAILABLE body code, got: " + body);
        }
    }

    @Nested
    @DisplayName("Invalid Token Handling Tests")
    class InvalidTokenHandlingTests {

        @Test
        @DisplayName("Should attempt auto-refresh for expired token")
        void doFilterInternal_expiredToken_attemptsAutoRefresh() throws Exception {
            String expiredToken = "expired.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(expiredToken);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(null);
            when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        @DisplayName("Should clear SecurityContext for malformed token")
        void doFilterInternal_malformedToken_clearsContext() throws Exception {
            String token = "malformed.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        @DisplayName("Should clear SecurityContext for invalid signature")
        void doFilterInternal_invalidSignature_clearsContext() throws Exception {
            String token = "tampered.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        @DisplayName("Should clear SecurityContext for revoked token")
        void doFilterInternal_revokedToken_clearsContext() throws Exception {
            String token = "revoked.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.REVOKED));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }
    }
}
