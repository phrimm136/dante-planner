package org.danteplanner.backend.auth.security;

import org.danteplanner.backend.shared.security.AuthDegradationResponder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.danteplanner.backend.auth.exception.InvalidTokenException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.auth.token.RefreshRotationService;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * <p>The filter authenticates from token claims alone (no per-request DB lookup): a valid
 * token authenticates without touching {@code Users}; deleted/demoted users are rejected
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
    private Users users;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private RefreshRotationService refreshRotationService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LogCapture securityEvents;

    @BeforeEach
    void setUp() {
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        filter = new JwtAuthenticationFilter(tokenValidator, tokenBlacklistService,
                new AccessTokenAuthenticator(tokenValidator, tokenBlacklistService), cookieUtils, users, new AuthDegradationResponder(new ProblemWriter(objectMapper)), tokenGenerator, refreshRotationService, new LineageRotationFlag(false), new JwtProperties());
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", "/test");
        response = new MockHttpServletResponse();
        // Capture WARN security events so audit-log rendering can be asserted. Both classes on
        // the authentication path feed the same appender: the assertions are about the event a
        // rejection renders, which no consumer keys to a logger name.
        securityEvents = LogCapture.onThisThread(JwtAuthenticationFilter.class, AccessTokenAuthenticator.class);
    }

    @AfterEach
    void tearDown() {
        securityEvents.close();
        // The filter under test populates SecurityContextHolder, whose default strategy is a
        // ThreadLocal that outlives this class. MockMvc runs its filter chain on the calling
        // thread, and JwtAuthenticationFilter sets a principal only when none is present, so a
        // leftover authentication makes a later class's request run as this test's user.
        SecurityContextHolder.clearContext();
    }

    private List<String> loggedMessages() {
        return securityEvents.events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> grantedAuthorities() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private TokenClaims createValidClaims(Long userId) {
        return new TokenClaims(
                userId,
                TokenClaims.TYPE_ACCESS,
                UserRole.NORMAL,
                new Date(),
                new Date(System.currentTimeMillis() + 3600000)
        );
    }

    private TokenClaims createRefreshClaims(Long userId) {
        return new TokenClaims(
                userId,
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
    class ActiveUserAuthenticationTests {

        @Test
        void doFilterInternal_WhenValidToken_AuthenticatesWithoutDbLookup() throws Exception {
            String token = "valid.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            lenient().when(tokenValidator.validateAccessToken(token)).thenReturn(createValidClaims(userId));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertEquals(List.of("ROLE_" + UserRole.NORMAL.getValue()), grantedAuthorities());
            // The hot path must not touch the DB (keeps auth alive during DB maintenance)
            verify(users, never()).findActiveById(any());
            verify(users, never()).findById(any());
        }
    }

    @Nested
    class InvalidatedUserTests {

        @Test
        void doFilterInternal_WhenInvalidatedUser_ClearsContextAndContinues() throws Exception {
            String token = "valid.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            lenient().when(tokenValidator.validateAccessToken(token)).thenReturn(createValidClaims(userId));
            // deleteAccount()/demotion called invalidateUserTokens → this returns true
            when(tokenBlacklistService.isUserTokenInvalidated(eq(userId), anyLong())).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "Invalidated user must not be authenticated");
            verify(users, never()).findActiveById(any());
        }
    }

    @Nested
    class SentinelUserProtectionTests {

        @Test
        void doFilterInternal_WhenSentinelUser_SkipsAuthentication() throws Exception {
            String token = "sentinel.jwt.token";
            Long sentinelId = 0L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            lenient().when(tokenValidator.validateAccessToken(token)).thenReturn(createValidClaims(sentinelId));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(users, never()).findActiveById(any());
        }
    }

    @Nested
    class NoTokenTests {

        @Test
        void doFilterInternal_WhenNoToken_ContinuesWithoutAuth() throws Exception {
            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(tokenValidator, never()).validateAccessToken(any());
            verify(tokenValidator, never()).validateRefreshToken(any());
        }
    }

    @Nested
    class DbUnavailableDuringRefreshTests {

        @Test
        void doFilterInternal_WhenExpiredToken_RefreshDbDown_Returns503() throws Exception {
            String expiredToken = "expired.jwt.token";
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(expiredToken));
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));
            lenient().when(tokenValidator.validateAccessToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(users.findActiveById(userId))
                    .thenThrow(new DataAccessResourceFailureException("DB unreachable"));

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilterInternal_WhenNoAccessToken_RefreshDbDown_Returns503() throws Exception {
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            // Access cookie absent (expiry/MaxAge desync) but a refresh cookie is present —
            // this routes through the token==null branch, a different catch site than the
            // expired-token path below.
            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.empty());
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(users.findActiveById(userId))
                    .thenThrow(new DataAccessResourceFailureException("DB unreachable"));

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilterInternal_WhenExpiredToken_RefreshTxBeginFails_Returns503() throws Exception {
            String expiredToken = "expired.jwt.token";
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(expiredToken));
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));
            lenient().when(tokenValidator.validateAccessToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            // findActiveById is @Transactional; a DB-down failure surfaces at transaction-begin
            // as CannotCreateTransactionException, NOT DataAccessResourceFailureException.
            when(users.findActiveById(userId))
                    .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilterInternal_WhenRefreshRedisWriteFails_ReturnsAuthTemporarilyUnavailable() throws Exception {
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;
            User user = activeUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.empty());
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(tokenBlacklistService.isBlacklisted(refreshToken)).thenReturn(false);
            when(tokenBlacklistService.isUserTokenInvalidated(eq(userId), anyLong())).thenReturn(false);
            when(users.findActiveById(userId)).thenReturn(Optional.of(UsersApiAdapter.toSummary(user)));
            doThrow(new org.springframework.data.redis.RedisConnectionFailureException("auth redis down"))
                    .when(tokenBlacklistService).blacklistTokenForRotation(eq(refreshToken), any());

            filter.doFilterInternal(request, response, filterChain);

            String body = response.getContentAsString();
            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
            assertEquals("AUTH_TEMPORARILY_UNAVAILABLE", (String) JsonPath.read(body, "$.code"));
            assertEquals((String) JsonPath.read(body, "$.detail"), (String) JsonPath.read(body, "$.message"));
            assertEquals("10", response.getHeader(HttpHeaders.RETRY_AFTER));
        }

        @Test
        void doFilterInternal_WhenRefreshDbDown_BodyCodeIsWriteTemporarilyUnavailable() throws Exception {
            String expiredToken = "expired.jwt.token";
            String refreshToken = "refresh.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(expiredToken));
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));
            lenient().when(tokenValidator.validateAccessToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(createRefreshClaims(userId));
            when(users.findActiveById(userId))
                    .thenThrow(new DataAccessResourceFailureException("DB unreachable"));

            filter.doFilterInternal(request, response, filterChain);

            String body = response.getContentAsString();
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
            assertEquals("WRITE_TEMPORARILY_UNAVAILABLE", (String) JsonPath.read(body, "$.code"));
            assertEquals((String) JsonPath.read(body, "$.detail"), (String) JsonPath.read(body, "$.message"));
        }
    }

    @Nested
    class InvalidTokenHandlingTests {

        @Test
        void doFilterInternal_WhenExpiredToken_AttemptsAutoRefresh() throws Exception {
            String expiredToken = "expired.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(expiredToken));
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.empty());
            lenient().when(tokenValidator.validateAccessToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        void doFilterInternal_WhenMalformedToken_ClearsContextAndLogsTokenInvalid() throws Exception {
            String token = "malformed.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            lenient().when(tokenValidator.validateAccessToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
            assertTrue(loggedMessages().stream().anyMatch(m -> m.contains("TOKEN_INVALID (MALFORMED)")),
                    "Malformed token must render its reason in the security event, got: " + loggedMessages());
        }

        @Test
        void doFilterInternal_WhenInvalidSignature_ClearsContextAndLogsTokenInvalid() throws Exception {
            String token = "tampered.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            lenient().when(tokenValidator.validateAccessToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
            assertTrue(loggedMessages().stream().anyMatch(m -> m.contains("TOKEN_INVALID (INVALID_SIGNATURE)")),
                    "Invalid-signature token must render its reason in the security event, got: " + loggedMessages());
        }

        @Test
        void doFilterInternal_WhenRevokedToken_ClearsContext() throws Exception {
            String token = "revoked.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            lenient().when(tokenValidator.validateAccessToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.REVOKED));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }
    }

    @Nested
    class TokenTypeEnforcementTests {

        @Test
        void doFilterInternal_WhenRefreshTypedTokenInAccessCookie_DoesNotAuthenticate() throws Exception {
            String token = "refresh.typed.in.access.cookie";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            lenient().when(tokenValidator.validateAccessToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "A refresh-typed token in the access cookie must NOT authenticate (INVALID_TYPE)");
        }

        @Test
        void doFilterInternal_WhenRefreshTypedTokenInAccessCookie_LogsInvalidTypeReason() throws Exception {
            String token = "refresh.typed.in.access.cookie";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.of(token));
            lenient().when(tokenValidator.validateAccessToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE));

            filter.doFilterInternal(request, response, filterChain);

            assertTrue(loggedMessages().stream().anyMatch(m -> m.contains("TOKEN_INVALID (INVALID_TYPE)")),
                    "Wrong-type access token must surface the INVALID_TYPE reason in the security event, got: "
                            + loggedMessages());
        }
    }

    @Nested
    class RefreshPathTokenTypeEnforcementTests {

        @Test
        void doFilterInternal_WhenAccessTypedTokenOnRefreshPath_UsesValidateRefreshToken() throws Exception {
            String refreshCookieToken = "access.typed.in.refresh.cookie";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(Optional.empty());
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(Optional.of(refreshCookieToken));
            lenient().when(tokenValidator.validateRefreshToken(refreshCookieToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE));

            filter.doFilterInternal(request, response, filterChain);

            // The reroute contract: the auto-refresh site must delegate to the refresh-typed validator.
            // Routing through validateAccessToken instead ends in the same guest outcome, so only a real
            // TokenValidator behind a real refresh cookie could separate the two by outcome alone.
            verify(tokenValidator).validateRefreshToken(refreshCookieToken);
            // Preserved-behavior anchor: still a guest, still no rotation.
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "An access-typed token on the refresh path must NOT authenticate");
            verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
        }
    }
}
