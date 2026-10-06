package org.danteplanner.backend.auth.security;

import org.danteplanner.backend.shared.security.AuthDegradationResponder;
import org.danteplanner.backend.shared.security.CustomAuthenticationEntryPoint;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.ScriptedFailuresCheck;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import com.jayway.jsonpath.JsonPath;
import org.springframework.data.redis.RedisConnectionFailureException;
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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * <p>The filter authenticates from token claims alone (no per-request DB lookup): a valid
 * token authenticates without touching {@code Users}; deleted/demoted users are rejected
 * via the in-memory {@code isUserTokenInvalidated} check; and a DB outage during the refresh
 * path returns 503 rather than a 500 or a silent guest downgrade.</p>
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class JwtAuthenticationFilterTest {

    private final TestJwt jwt = new TestJwt();

    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(jwt.clock());

    private final CookieUtils cookieUtils = new CookieUtils(false, "", "Lax");

    private final FakeUsers users = new FakeUsers();

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
        filter = filterOver(users);
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

    private JwtAuthenticationFilter filterOver(Users lookup) {
        return new JwtAuthenticationFilter(jwt.service(), tokenBlacklist,
                new AccessTokenAuthenticator(jwt.service(), tokenBlacklist), cookieUtils, lookup, new AuthDegradationResponder(new ProblemWriter(objectMapper)), jwt.service(), null, new LineageRotationFlag(false), jwt.properties());
    }

    private static FakeUsers usersFailingWith(RuntimeException failure) {
        return new FakeUsers() {
            @Override
            public Optional<UserSummary> findActiveById(Long userId) {
                throw failure;
            }

            @Override
            public UserSummary findById(Long id) {
                throw failure;
            }
        };
    }

    private List<String> loggedMessages() {
        return securityEvents.events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> grantedAuthorities() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private String accessToken(Long userId) {
        return jwt.service().generateAccessToken(userId, UserRole.NORMAL);
    }

    private String refreshToken(Long userId) {
        return jwt.service().generateRefreshToken(userId);
    }

    private TestJwt anHourAgo() {
        return new TestJwt(jwt.clock().instant().minus(Duration.ofHours(1)));
    }

    private String expiredAccessToken(Long userId) {
        return anHourAgo().service().generateAccessToken(userId, UserRole.NORMAL);
    }

    private String tamperedAccessToken(Long userId) {
        String[] signed = accessToken(userId).split("\\.");
        String[] other = accessToken(userId + 1).split("\\.");
        return signed[0] + "." + other[1] + "." + signed[2];
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
            Long userId = 123L;
            String token = accessToken(userId);

            // The hot path must not touch the DB (keeps auth alive during DB maintenance)
            filter = filterOver(usersFailingWith(new DataAccessResourceFailureException("DB unreachable")));
            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, token));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertEquals(List.of("ROLE_" + UserRole.NORMAL.getValue()), grantedAuthorities());
        }
    }

    @Nested
    class InvalidatedUserTests {

        @Test
        void doFilterInternal_WhenInvalidatedUser_ClearsContextAndContinues() throws Exception {
            Long userId = 123L;
            jwt.clock().advance(Duration.ofMinutes(-1));
            String token = accessToken(userId);
            jwt.clock().advance(Duration.ofMinutes(1));

            filter = filterOver(usersFailingWith(new DataAccessResourceFailureException("DB unreachable")));
            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, token));
            tokenBlacklist.invalidateUserTokens(userId);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "Invalidated user must not be authenticated");
        }
    }

    @Nested
    class SentinelUserProtectionTests {

        @Test
        void doFilterInternal_WhenSentinelUser_SkipsAuthentication() throws Exception {
            String token = accessToken(Users.SENTINEL_USER_ID);

            filter = filterOver(usersFailingWith(new DataAccessResourceFailureException("DB unreachable")));
            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, token));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Nested
    class NoTokenTests {

        @Test
        void doFilterInternal_WhenNoToken_ContinuesWithoutAuth() throws Exception {

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertThat(response.getCookies()).isEmpty();
            assertThat(loggedMessages()).isEmpty();
        }
    }

    @Nested
    class DbUnavailableDuringRefreshTests {

        @Test
        void doFilterInternal_WhenExpiredToken_RefreshDbDown_Returns503() throws Exception {
            Long userId = 123L;

            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, expiredAccessToken(userId)), new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken(userId)));
            filter = filterOver(usersFailingWith(new DataAccessResourceFailureException("DB unreachable")));

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilterInternal_WhenNoAccessToken_RefreshDbDown_Returns503() throws Exception {
            Long userId = 123L;

            // Access cookie absent (expiry/MaxAge desync) but a refresh cookie is present —
            // this routes through the token==null branch, a different catch site than the
            // expired-token path below.
            request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken(userId)));
            filter = filterOver(usersFailingWith(new DataAccessResourceFailureException("DB unreachable")));

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilterInternal_WhenExpiredToken_RefreshTxBeginFails_Returns503() throws Exception {
            Long userId = 123L;

            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, expiredAccessToken(userId)), new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken(userId)));
            // findActiveById is @Transactional; a DB-down failure surfaces at transaction-begin
            // as CannotCreateTransactionException, NOT DataAccessResourceFailureException.
            filter = filterOver(usersFailingWith(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction")));

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilterInternal_WhenRefreshRedisWriteFails_ReturnsAuthTemporarilyUnavailable() throws Exception {
            Long userId = 123L;
            User user = activeUser(userId);
            RedisConnectionFailureException authRedisDown = new RedisConnectionFailureException("auth redis down");

            request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken(userId)));
            users.add(user);
            tokenBlacklist.failNext(RecordingTokenBlacklist.Operation.BLACKLIST_TOKEN_FOR_ROTATION, authRedisDown);

            filter.doFilterInternal(request, response, filterChain);

            String body = response.getContentAsString();
            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            verify(filterChain, never()).doFilter(any(), any());
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
            assertEquals("AUTH_TEMPORARILY_UNAVAILABLE", (String) JsonPath.read(body, "$.code"));
            assertEquals((String) JsonPath.read(body, "$.detail"), (String) JsonPath.read(body, "$.message"));
            assertEquals("10", response.getHeader(HttpHeaders.RETRY_AFTER));
            assertThat(tokenBlacklist.failed()).containsExactly(new RecordingTokenBlacklist.Failed(
                    RecordingTokenBlacklist.Operation.BLACKLIST_TOKEN_FOR_ROTATION, authRedisDown));
        }

        @Test
        void doFilterInternal_WhenRefreshDbDown_BodyCodeIsWriteTemporarilyUnavailable() throws Exception {
            Long userId = 123L;

            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, expiredAccessToken(userId)), new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken(userId)));
            filter = filterOver(usersFailingWith(new DataAccessResourceFailureException("DB unreachable")));

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
            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, expiredAccessToken(123L)));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        void doFilterInternal_WhenMalformedToken_ClearsContextAndLogsTokenInvalid() throws Exception {
            String token = "malformed.jwt.token";

            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, token));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
            assertTrue(loggedMessages().stream().anyMatch(m -> m.contains("TOKEN_INVALID (MALFORMED)")),
                    "Malformed token must render its reason in the security event, got: " + loggedMessages());
        }

        @Test
        void doFilterInternal_WhenInvalidSignature_ClearsContextAndLogsTokenInvalid() throws Exception {
            String token = tamperedAccessToken(123L);

            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, token));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
            assertTrue(loggedMessages().stream().anyMatch(m -> m.contains("TOKEN_INVALID (INVALID_SIGNATURE)")),
                    "Invalid-signature token must render its reason in the security event, got: " + loggedMessages());
        }

        @Test
        void doFilterInternal_WhenRevokedToken_ClearsContext() throws Exception {
            String token = accessToken(123L);
            tokenBlacklist.blacklistToken(token, jwt.service().validateAccessToken(token).expiration());

            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, token));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }
    }

    @Nested
    class TokenTypeEnforcementTests {

        @Test
        void doFilterInternal_WhenRefreshTypedTokenInAccessCookie_DoesNotAuthenticate() throws Exception {
            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, refreshToken(123L)));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "A refresh-typed token in the access cookie must NOT authenticate (INVALID_TYPE)");
        }

        @Test
        void doFilterInternal_WhenRefreshTypedTokenInAccessCookie_LogsInvalidTypeReason() throws Exception {
            request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, refreshToken(123L)));

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
            request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, accessToken(123L)));

            filter.doFilterInternal(request, response, filterChain);

            // The reroute contract: the auto-refresh site must delegate to the refresh-typed validator.
            assertNull(request.getAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE));
            assertNull(response.getCookie(CookieConstants.REFRESH_TOKEN));
            // Preserved-behavior anchor: still a guest, still no rotation.
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "An access-typed token on the refresh path must NOT authenticate");
            assertEquals(0, tokenBlacklist.size());
        }
    }
}
