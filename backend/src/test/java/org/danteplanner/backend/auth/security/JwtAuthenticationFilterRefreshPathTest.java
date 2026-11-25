package org.danteplanner.backend.auth.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationOutcome;
import org.danteplanner.backend.auth.token.TokenGenerationException;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.security.AuthDegradationResponder;
import org.danteplanner.backend.shared.security.CustomAuthenticationEntryPoint;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.RecordingRefreshRotation;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TestJsonMappers;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(JwtAuthenticationFilterRefreshPathTest.FILTER_LOG_LEVEL)
class JwtAuthenticationFilterRefreshPathTest {

    /** This class raises the level of a logger the whole JVM shares. */
    static final String FILTER_LOG_LEVEL = "jwt-authentication-filter-log-level";

    private static final Long USER_ID = 42L;

    private static final Authentication EARLIER = new TestingAuthenticationToken("earlier", null);

    private final List<String> fetches = new ArrayList<>();

    private final TestJwt jwt = new TestJwt();

    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(jwt.clock()) {
        @Override
        public boolean isBlacklisted(String token) {
            fetches.add("blacklist");
            return super.isBlacklisted(token);
        }

        @Override
        public boolean isUserTokenInvalidated(Long userId, long issuedAt) {
            fetches.add("invalidation");
            return super.isUserTokenInvalidated(userId, issuedAt);
        }
    };

    private final FakeUsers users = new FakeUsers() {
        @Override
        public Optional<UserSummary> findActiveById(Long userId) {
            fetches.add("user");
            return super.findActiveById(userId);
        }
    };

    private final CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private String refreshToken;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;
    private Level configuredLevel;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        refreshToken = jwt.service().generateRefreshToken(USER_ID);
        request = new MockHttpServletRequest("GET", "/test");
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        configuredLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);
        logs = LogCapture.onThisThread(JwtAuthenticationFilter.class);
    }

    @AfterEach
    void tearDown() {
        logs.close();
        filterLogger.setLevel(configuredLevel);
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_WhenTheRefreshIsGood_FetchesInOrderAndWritesTheRefreshCookieFirst() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        present(refreshToken);

        filter(rotatingTo(new RotationOutcome.Rotated("successor", jwt.service().validateRefreshToken(refreshToken))))
                .doFilterInternal(request, response, filterChain);

        assertThat(fetches).containsExactly("blacklist", "invalidation", "user", "rotation");
        assertThat(writtenCookies()).containsExactly(CookieConstants.REFRESH_TOKEN, CookieConstants.ACCESS_TOKEN);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(USER_ID);
        assertThat(logged()).containsExactly("DEBUG Auto-refreshed tokens for user: test@example.com");
        assertTheChainRan();
    }

    @Test
    void doFilterInternal_WhenThereIsNoToken_ContinuesKeepingTheEarlierAuthentication() throws Exception {
        authenticateEarlier();

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(EARLIER);
        assertThat(fetches).isEmpty();
        assertThat(logged()).containsExactly("DEBUG No refresh token available for auto-refresh");
        assertTheChainRan();
    }

    @Test
    void doFilterInternal_WhenTheRefreshCookieHoldsAnAccessToken_StaysAGuestKeepingTheCookies() throws Exception {
        authenticateEarlier();
        present(jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL));

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertSilentGuest("Auto-refresh failed: Token type is invalid for this operation");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(EARLIER);
        assertThat(fetches).isEmpty();
    }

    @Test
    void doFilterInternal_WhenTheRefreshTokenIsBlacklisted_AbandonsBeforeAskingForTheInvalidation() throws Exception {
        tokenBlacklist.blacklistToken(refreshToken, jwt.service().validateRefreshToken(refreshToken).expiration());
        authenticateEarlier();
        present(refreshToken);

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.SESSION_REVOKED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(EARLIER);
        assertThat(fetches).containsExactly("blacklist");
        assertThat(logged()).containsExactly("WARN Attempted auto-refresh with blacklisted token for user: 42");
    }

    @Test
    void doFilterInternal_WhenTheUsersTokensAreInvalidated_AbandonsBeforeAskingForTheUser() throws Exception {
        jwt.clock().advance(Duration.ofMinutes(1));
        tokenBlacklist.invalidateUserTokens(USER_ID);
        present(refreshToken);

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.SESSION_REVOKED);
        assertThat(fetches).containsExactly("blacklist", "invalidation");
        assertThat(logged()).containsExactly("WARN Attempted auto-refresh for user with invalidated tokens: 42");
    }

    @Test
    void doFilterInternal_WhenTheUserIsGone_AbandonsBeforeRotating() throws Exception {
        present(refreshToken);

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.SESSION_REVOKED);
        assertThat(fetches).containsExactly("blacklist", "invalidation", "user");
        assertThat(logged()).containsExactly("WARN Attempted auto-refresh for non-existent or deleted user: 42");
    }

    @Test
    void doFilterInternal_WhenTheRotationRejectsTheTokenAsInvalid_AbandonsAsInvalidToken() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        present(refreshToken);

        filter(rotatingTo(new RotationOutcome.Rejected(RotationOutcome.Rejected.Reason.INVALID)))
                .doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.INVALID_TOKEN);
        assertThat(logged()).isEmpty();
    }

    @Test
    void doFilterInternal_WhenTheRotationFamilyIsRevoked_AbandonsAsSessionRevoked() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        present(refreshToken);

        filter(rotatingTo(new RotationOutcome.Revoked("family")))
                .doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.SESSION_REVOKED);
        assertThat(logged()).isEmpty();
    }

    @Test
    void doFilterInternal_WhenTheRotationThrowsInvalidToken_AbandonsAsInvalidToken() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        present(refreshToken);

        filter(failingWith(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED)))
                .doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.INVALID_TOKEN);
        assertThat(logged()).isEmpty();
    }

    @Test
    void doFilterInternal_WhenTheRotationFailsUnexpectedly_ContinuesAsGuestKeepingTheCookies() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        present(refreshToken);

        filter(failingWith(new IllegalStateException("boom"))).doFilterInternal(request, response, filterChain);

        assertSilentGuest("Auto-refresh failed: boom");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_WhenTheAccessTokenCannotBeMinted_KeepsTheRotatedRefreshCookie() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        present(refreshToken);

        filterOver(rotatingTo(new RotationOutcome.Rotated("successor",
                jwt.service().validateRefreshToken(refreshToken))), refusingToMint())
                .doFilterInternal(request, response, filterChain);

        assertThat(writtenCookies()).containsExactly(CookieConstants.REFRESH_TOKEN);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(logged()).containsExactly("DEBUG Auto-refresh failed: no key");
        assertTheChainRan();
    }

    @Test
    void doFilterInternal_WhenTheRotationTimesOut_ReportsTheOutageWritingNoCookie() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        authenticateEarlier();
        presentExpiredAccessAnd(refreshToken);

        filter(failingWith(new QueryTimeoutException("slow"))).doFilterInternal(request, response, filterChain);

        assertOutage("AUTH_TEMPORARILY_UNAVAILABLE");
    }

    @Test
    void doFilterInternal_WhenTheRotationFailsOnTheDatabase_ReportsTheOutageWritingNoCookie() throws Exception {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        authenticateEarlier();
        presentExpiredAccessAnd(refreshToken);

        filter(failingWith(new DataAccessResourceFailureException("down")))
                .doFilterInternal(request, response, filterChain);

        assertOutage("WRITE_TEMPORARILY_UNAVAILABLE");
    }

    @Test
    void doFilterInternal_WhenTheExpiredSessionHasNoRefreshToken_ClearsTheContext() throws Exception {
        authenticateEarlier();
        request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, expiredAccessToken()));

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(logged()).containsExactly("DEBUG Access token expired, attempting auto-refresh",
                "DEBUG No refresh token available for auto-refresh");
        assertTheChainRan();
    }

    @Test
    void doFilterInternal_WhenTheExpiredSessionIsAbandoned_ClearsTheContext() throws Exception {
        authenticateEarlier();
        presentExpiredAccessAnd(refreshToken);

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertAbandoned(CustomAuthenticationEntryPoint.SESSION_REVOKED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_WhenTheAccessTokenIsRejected_ClearsTheContextWithoutRefreshing() throws Exception {
        authenticateEarlier();
        request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, "malformed"),
                new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));

        filter(rotation()).doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(fetches).isEmpty();
        assertThat(writtenCookies()).isEmpty();
        assertTheChainRan();
    }

    private JwtAuthenticationFilter filter(RefreshRotationService rotation) {
        return filterOver(rotation, jwt.service());
    }

    private JwtAuthenticationFilter filterOver(RefreshRotationService rotation, TokenGenerator tokenGenerator) {
        return new JwtAuthenticationFilter(jwt.service(), tokenBlacklist,
                new AccessTokenAuthenticator(jwt.service(), tokenBlacklist), cookieUtils, users,
                new AuthDegradationResponder(new ProblemWriter(TestJsonMappers.springDefaults())),
                tokenGenerator, rotation, jwt.properties());
    }

    private RefreshRotationService rotation() {
        return failingWith(new AssertionError("this path does not rotate"));
    }

    private RefreshRotationService rotatingTo(RotationOutcome outcome) {
        return new RecordingRefreshRotation(jwt, tokenBlacklist) {
            @Override
            public RotationOutcome rotate(String presented) {
                fetches.add("rotation");
                return outcome;
            }
        };
    }

    private RefreshRotationService failingWith(Throwable failure) {
        return new RecordingRefreshRotation(jwt, tokenBlacklist) {
            @Override
            public RotationOutcome rotate(String presented) {
                if (failure instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) failure;
            }
        };
    }

    private static TokenGenerator refusingToMint() {
        return new TokenGenerator() {
            @Override
            public String generateAccessToken(Long userId, UserRole role) {
                throw new TokenGenerationException("no key", new IllegalStateException());
            }

            @Override
            public String generateRefreshToken(Long userId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String generateRefreshToken(Long userId, String familyId, String parentJti) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private String expiredAccessToken() {
        return new TestJwt(jwt.clock().instant().minus(Duration.ofHours(1))).service()
                .generateAccessToken(USER_ID, UserRole.NORMAL);
    }

    private void present(String token) {
        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, token));
    }

    private void presentExpiredAccessAnd(String token) {
        request.setCookies(new Cookie(CookieConstants.ACCESS_TOKEN, expiredAccessToken()),
                new Cookie(CookieConstants.REFRESH_TOKEN, token));
    }

    private void authenticateEarlier() {
        SecurityContextHolder.getContext().setAuthentication(EARLIER);
    }

    private List<String> writtenCookies() {
        return Arrays.stream(response.getCookies()).map(Cookie::getName).toList();
    }

    private List<String> logged() {
        return logs.events().stream()
                .map((ILoggingEvent event) -> event.getLevel() + " " + event.getFormattedMessage())
                .toList();
    }

    private void assertTheChainRan() {
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(MDC.get("path")).isEqualTo("/test");
    }

    private void assertSilentGuest(String reason) {
        assertThat(writtenCookies()).isEmpty();
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE)).isNull();
        assertThat(logged()).containsExactly("DEBUG " + reason);
        assertTheChainRan();
    }

    private void assertAbandoned(String errorCode) {
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE)).isEqualTo(errorCode);
        assertThat(writtenCookies()).containsExactly(CookieConstants.ACCESS_TOKEN, CookieConstants.REFRESH_TOKEN);
        assertThat(response.getCookies()).allSatisfy(cookie -> assertThat(cookie.getMaxAge()).isZero());
        assertTheChainRan();
    }

    private void assertOutage(String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains(code);
        assertThat(writtenCookies()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
