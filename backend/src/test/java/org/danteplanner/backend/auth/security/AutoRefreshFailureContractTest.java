package org.danteplanner.backend.auth.security;

import org.danteplanner.backend.shared.security.AuthDegradationResponder;
import org.danteplanner.backend.shared.security.CustomAuthenticationEntryPoint;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.auth.token.RotationOutcome;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.danteplanner.backend.support.TestJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;

class AutoRefreshFailureContractTest {

    private enum DeadToken {
        SPENT,
        BLACKLISTED
    }

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

    @ParameterizedTest
    @EnumSource(DeadToken.class)
    void autoRefresh_WhenRefreshTokenIsDead_WithdrawsBothCookies(DeadToken dead) throws Exception {
        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, deadRefreshToken));
        givenTheTokenIsDead(dead);

        filter().doFilterInternal(request, response, filterChain);

        assertCookieWithdrawn(CookieConstants.ACCESS_TOKEN);
        assertCookieWithdrawn(CookieConstants.REFRESH_TOKEN);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("a dead token must not authenticate the request")
                .isNull();
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE))
                .as("the client is owed the reason, so it can stop retrying")
                .isEqualTo(CustomAuthenticationEntryPoint.SESSION_REVOKED);
        assertTheDeadTokenWasRefused(dead);
    }

    private void givenTheTokenIsDead(DeadToken dead) {
        users.add(TestDataFactory.unsavedUser(USER_ID));
        if (dead == DeadToken.SPENT) {
            RotationOutcome.Rotated successor = (RotationOutcome.Rotated) refreshRotation.rotate(deadRefreshToken);
            refreshRotation.rotate(successor.newRefreshJwt());
        } else {
            tokenBlacklist.blacklistToken(deadRefreshToken,
                    jwt.service().validateRefreshToken(deadRefreshToken).expiration());
        }
    }

    private void assertTheDeadTokenWasRefused(DeadToken dead) {
        if (dead == DeadToken.SPENT) {
            assertThat(refreshRotation.rotations().getLast()).isEqualTo(new RecordingRefreshRotation.Rotation(
                    deadRefreshToken,
                    new RotationOutcome.Revoked(TestJwt.familyIdOf(jwt.service().validateRefreshToken(deadRefreshToken)))));
        } else {
            assertThat(refreshRotation.calls()).isEmpty();
        }
    }

    private void assertCookieWithdrawn(String name) {
        Cookie cookie = response.getCookie(name);
        assertThat(cookie).as(name + " must be withdrawn, not left with the client").isNotNull();
        assertThat(cookie.getMaxAge()).as(name + " must be withdrawn, not reissued").isEqualTo(0);
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(
                jwt.service(), tokenBlacklist,
                new AccessTokenAuthenticator(jwt.service(), tokenBlacklist), cookieUtils, users,
                new AuthDegradationResponder(new ProblemWriter(TestJsonMappers.springDefaults())), jwt.service(), refreshRotation,
                jwt.properties());
    }
}
