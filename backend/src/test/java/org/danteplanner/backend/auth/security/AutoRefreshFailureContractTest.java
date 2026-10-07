package org.danteplanner.backend.auth.security;

import org.danteplanner.backend.shared.security.AuthDegradationResponder;
import org.danteplanner.backend.shared.security.CustomAuthenticationEntryPoint;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingRefreshRotation;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * One contract, both rotation implementations.
 *
 * <p>{@code jwt.rotation.lineage-enabled} selects between two auto-refresh implementations that owe
 * their client the same thing. Tests written per implementation cannot see them diverge, and one
 * did: only the lineage half withdrew the cookies of a token it had just rejected, so under the
 * legacy half a client re-presented dead credentials on every request and the session resolved to
 * neither authenticated nor guest.</p>
 *
 * <p>What each implementation recognizes as a dead token differs, so the arrangement is per branch;
 * the assertion is not. A third implementation added behind this flag gains this contract by
 * appearing in the value source.</p>
 */
class AutoRefreshFailureContractTest {

    private static final Long USER_ID = 42L;

    private final TestJwt jwt = new TestJwt();
    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(jwt.clock());
    private final RecordingRefreshRotation refreshRotation = new RecordingRefreshRotation(jwt, tokenBlacklist);

    private final FakeUsers users = new FakeUsers();
    private final String deadRefreshToken = jwt.service().generateRefreshToken(USER_ID);
    private final CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "lineage-enabled={0}")
    @ValueSource(booleans = {true, false})
    void autoRefresh_WhenRefreshTokenIsDead_WithdrawsBothCookies(boolean lineageEnabled)
            throws Exception {
        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, deadRefreshToken));
        givenTheTokenIsDead(lineageEnabled);

        filterWithFlag(lineageEnabled).doFilterInternal(request, response, filterChain);

        assertCookieWithdrawn(CookieConstants.ACCESS_TOKEN);
        assertCookieWithdrawn(CookieConstants.REFRESH_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "a dead token must not authenticate the request");
        assertEquals(CustomAuthenticationEntryPoint.SESSION_REVOKED,
                request.getAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE),
                "the client is owed the reason, so it can stop retrying");
        assertTheDeadTokenWasRefused(lineageEnabled);
    }

    /**
     * Each implementation recognizes a spent token its own way: the lineage state machine reports a
     * revoked family, and the legacy path finds the token on the rotation blacklist.
     */
    private void givenTheTokenIsDead(boolean lineageEnabled) {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        if (lineageEnabled) {
            RotationResult.Rotated successor = (RotationResult.Rotated) refreshRotation.rotate(deadRefreshToken);
            refreshRotation.rotate(successor.newRefreshJwt());
        } else {
            tokenBlacklist.blacklistToken(deadRefreshToken,
                    jwt.service().validateRefreshToken(deadRefreshToken).expiration());
        }
    }

    private void assertTheDeadTokenWasRefused(boolean lineageEnabled) {
        if (lineageEnabled) {
            assertThat(refreshRotation.rotations().getLast()).isEqualTo(new RecordingRefreshRotation.Rotation(
                    deadRefreshToken,
                    new RotationResult.Revoked(jwt.service().validateRefreshToken(deadRefreshToken).familyId())));
        } else {
            assertThat(refreshRotation.calls()).isEmpty();
        }
    }

    private void assertCookieWithdrawn(String name) {
        Cookie cookie = response.getCookie(name);
        assertNotNull(cookie, name + " must be withdrawn, not left with the client");
        assertEquals(0, cookie.getMaxAge(), name + " must be withdrawn, not reissued");
    }

    private JwtAuthenticationFilter filterWithFlag(boolean lineageEnabled) {
        return new JwtAuthenticationFilter(
                jwt.service(), tokenBlacklist,
                new AccessTokenAuthenticator(jwt.service(), tokenBlacklist), cookieUtils, users,
                new AuthDegradationResponder(new ProblemWriter(Jackson2ObjectMapperBuilder.json().build())), jwt.service(), refreshRotation,
                new LineageRotationFlag(lineageEnabled), jwt.properties());
    }
}
