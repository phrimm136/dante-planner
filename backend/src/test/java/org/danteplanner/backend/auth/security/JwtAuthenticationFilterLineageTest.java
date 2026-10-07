package org.danteplanner.backend.auth.security;

import org.danteplanner.backend.shared.security.AuthDegradationResponder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import jakarta.servlet.http.Cookie;

import java.time.Duration;
import java.util.List;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingRefreshRotation;
import org.danteplanner.backend.support.RecordingRefreshRotation.Rotation;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Integration tests for {@link JwtAuthenticationFilter} lineage-rotation behavior.
 *
 * <p>Covers both flag states: flag off must behave identically to the legacy
 * blacklist-on-rotation path (delegating to {@link TokenBlacklistService}), and
 * flag on must delegate auto-refresh to {@link RefreshRotationService}.</p>
 */
class JwtAuthenticationFilterLineageTest {

    private final TestJwt jwt = new TestJwt();

    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(jwt.clock());

    private final FakeUsers users = new FakeUsers();

    private final RecordingRefreshRotation refreshRotation = new RecordingRefreshRotation(jwt, tokenBlacklist);

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");

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
        return filterWithFlag(lineageEnabled, refreshRotation);
    }

    private JwtAuthenticationFilter filterWithFlag(boolean lineageEnabled, RefreshRotationService rotation) {
        return new JwtAuthenticationFilter(
                jwt.service(), tokenBlacklist,
                new AccessTokenAuthenticator(jwt.service(), tokenBlacklist), cookieUtils, users,
                new AuthDegradationResponder(new ProblemWriter(objectMapper)), jwt.service(), rotation, new LineageRotationFlag(lineageEnabled),
                jwt.properties());
    }

    private String freshLogin(Long userId) {
        return jwt.service().generateRefreshToken(userId);
    }

    private String familyOf(String refreshToken) {
        return jwt.service().validateRefreshToken(refreshToken).familyId();
    }

    private RotationResult.Rotated rotateAhead(RefreshRotationService rotation, String refreshToken) {
        return (RotationResult.Rotated) rotation.rotate(refreshToken);
    }

    @Test
    void doFilterInternal_WhenFlagOff_UsesLegacyRotation() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(false);
        Long userId = 42L;
        String refreshToken = jwt.legacyRefreshToken(userId);
        User user = TestDataFactory.unsavedUser(userId);

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(user);

        filter.doFilterInternal(request, response, filterChain);

        // Minting the successor here rather than taking it from the rotation service is what
        // distinguishes the legacy path; the cookie the client ends up with is the same shape.
        assertAccessCookieFor(userId);
        Cookie refreshCookie = assertRefreshCookieFor(userId);
        assertNotEquals(refreshToken, refreshCookie.getValue());
        assertNull(jwt.service().validateRefreshToken(refreshCookie.getValue()).parentJti(),
                "the legacy path mints a new family rather than rotating the presented one");
        jwt.clock().advance(Duration.ofSeconds(5));
        assertThat(tokenBlacklist.blacklistedTokens()).containsExactly(refreshToken);
        assertThat(refreshRotation.calls()).isEmpty();
        assertSame(request, filterChain.getRequest());
        assertSame(response, filterChain.getResponse());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndUnusedLatest_RotatesAndAuthenticates() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 7L;
        String refreshToken = freshLogin(userId);
        User user = TestDataFactory.unsavedUser(userId);

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(user);

        filter.doFilterInternal(request, response, filterChain);

        assertAccessCookieFor(userId);
        assertRefreshCookie(rotatedTo(refreshToken));
        assertEquals(0, tokenBlacklist.size());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndRetiredToken_RevokesFamilyNoAuth() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 21L;
        String refreshToken = freshLogin(userId);
        refreshRotation.rotate(rotateAhead(refreshRotation, refreshToken).newRefreshJwt());

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(TestDataFactory.unsavedUser(userId));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(refreshRotation.rotations().getLast())
                .isEqualTo(new Rotation(refreshToken, new RotationResult.Revoked(familyOf(refreshToken))));
        assertSessionCleared();
        assertSame(request, filterChain.getRequest());
        assertSame(response, filterChain.getResponse());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndSupersededToken_RevokesFamilyNoAuth() throws Exception {
        RecordingRefreshRotation noReuseWindow = new RecordingRefreshRotation(jwt, tokenBlacklist, true, 0L);
        JwtAuthenticationFilter filter = filterWithFlag(true, noReuseWindow);
        Long userId = 22L;
        String login = freshLogin(userId);
        String superseded = rotateAhead(noReuseWindow, login).newRefreshJwt();
        noReuseWindow.rotate(login);

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, superseded));
        users.add(TestDataFactory.unsavedUser(userId));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(noReuseWindow.rotations().getLast())
                .isEqualTo(new Rotation(superseded, new RotationResult.Revoked(familyOf(login))));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertSessionCleared();
    }

    @Test
    void doFilterInternal_WhenFlagOnAndRevokedFamily_RejectedNoAuth() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 23L;
        String refreshToken = freshLogin(userId);
        refreshRotation.revokeFamily(familyOf(refreshToken));

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(TestDataFactory.unsavedUser(userId));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(refreshRotation.rotations()).containsExactly(new Rotation(refreshToken,
                new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY)));
        assertSessionCleared();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndLegacyAdmit_AdmitsAndAuthenticates() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 99L;
        String refreshToken = jwt.legacyRefreshToken(userId);
        User user = TestDataFactory.unsavedUser(userId);

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(user);

        filter.doFilterInternal(request, response, filterChain);

        assertAccessCookieFor(userId);
        assertRefreshCookie(rotatedTo(refreshToken));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndLegacyAdmitOff_RejectedNoAuth() throws Exception {
        RecordingRefreshRotation legacyAdmitOff = new RecordingRefreshRotation(jwt, tokenBlacklist, false,
                RecordingRefreshRotation.DEFAULT_RETRY_REUSE_WINDOW_MS);
        JwtAuthenticationFilter filter = filterWithFlag(true, legacyAdmitOff);
        Long userId = 24L;
        String refreshToken = jwt.legacyRefreshToken(userId);

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(TestDataFactory.unsavedUser(userId));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(legacyAdmitOff.rotations()).containsExactly(new Rotation(refreshToken,
                new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID)));
        assertSessionCleared();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndExpiredAccess_TriggersLineageRefresh() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 11L;
        String accessToken = new TestJwt(jwt.clock().instant().minus(Duration.ofHours(1))).service()
                .generateAccessToken(userId, UserRole.NORMAL);
        String refreshToken = freshLogin(userId);
        User user = TestDataFactory.unsavedUser(userId);

        request.setCookies(
                new Cookie(CookieConstants.ACCESS_TOKEN, accessToken),
                new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));
        users.add(user);

        filter.doFilterInternal(request, response, filterChain);

        Cookie accessCookie = assertAccessCookieFor(userId);
        assertNotEquals(accessToken, accessCookie.getValue());
        assertRefreshCookie(rotatedTo(refreshToken));
        assertEquals(0, tokenBlacklist.size());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndBlacklistedToken_AbandonsWithoutRotating() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 31L;
        String refreshToken = freshLogin(userId);
        tokenBlacklist.blacklistToken(refreshToken, jwt.service().validateRefreshToken(refreshToken).expiration());
        users.add(TestDataFactory.unsavedUser(userId));

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(refreshRotation.calls()).isEmpty();
        assertSessionCleared();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_WhenFlagOnAndUserTokensInvalidated_AbandonsWithoutRotating() throws Exception {
        JwtAuthenticationFilter filter = filterWithFlag(true);
        Long userId = 32L;
        jwt.clock().advance(Duration.ofMinutes(-1));
        String refreshToken = freshLogin(userId);
        jwt.clock().advance(Duration.ofMinutes(1));
        tokenBlacklist.invalidateUserTokens(userId);
        users.add(TestDataFactory.unsavedUser(userId));

        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, refreshToken));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(refreshRotation.calls()).isEmpty();
        assertSessionCleared();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private String rotatedTo(String presented) {
        Rotation rotation = refreshRotation.rotations().getLast();
        assertEquals(presented, rotation.presented());
        return ((RotationResult.Rotated) rotation.result()).newRefreshJwt();
    }

    private Cookie assertAccessCookieFor(Long userId) {
        Cookie accessCookie = response.getCookie(CookieConstants.ACCESS_TOKEN);
        assertNotNull(accessCookie);
        TokenClaims claims = jwt.service().validateAccessToken(accessCookie.getValue());
        assertEquals(userId, claims.userId());
        assertEquals(jwt.properties().getAccessTokenExpirySeconds(), accessCookie.getMaxAge());
        return accessCookie;
    }

    private Cookie assertRefreshCookieFor(Long userId) {
        Cookie refreshCookie = response.getCookie(CookieConstants.REFRESH_TOKEN);
        assertNotNull(refreshCookie);
        assertEquals(userId, jwt.service().validateRefreshToken(refreshCookie.getValue()).userId());
        assertEquals(jwt.properties().getRefreshTokenExpirySeconds(), refreshCookie.getMaxAge());
        return refreshCookie;
    }

    /**
     * The successor a rotation hands back reaches the client, since nothing downstream of the
     * filter writes it.
     */
    private void assertRefreshCookie(String expectedValue) {
        Cookie refreshCookie = response.getCookie(CookieConstants.REFRESH_TOKEN);
        assertNotNull(refreshCookie);
        assertEquals(expectedValue, refreshCookie.getValue());
        assertEquals(jwt.properties().getRefreshTokenExpirySeconds(), refreshCookie.getMaxAge());
    }

    /**
     * Both auth cookies carry a deletion rather than a token. A client left holding dead cookies
     * re-presents them on every later request, so a rejected rotation has to withdraw them, not
     * merely decline to mint a replacement.
     */
    private void assertSessionCleared() {
        for (String name : List.of(CookieConstants.ACCESS_TOKEN, CookieConstants.REFRESH_TOKEN)) {
            Cookie cookie = response.getCookie(name);
            assertNotNull(cookie, name + " must be withdrawn");
            assertEquals(0, cookie.getMaxAge(), name + " must be withdrawn, not reissued");
        }
    }
}
