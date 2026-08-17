package org.danteplanner.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.when;

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
@ExtendWith(MockitoExtension.class)
class AutoRefreshFailureContractTest {

    private static final String DEAD_REFRESH_TOKEN = "dead.refresh.jwt";
    private static final Long USER_ID = 42L;

    @Mock private TokenValidator tokenValidator;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private UserService userService;
    @Mock private ObjectMapper objectMapper;
    @Mock private TokenGenerator tokenGenerator;
    @Mock private RefreshRotationService refreshRotationService;
    @Mock private JwtProperties jwtProperties;

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
        request.setCookies(new Cookie(CookieConstants.REFRESH_TOKEN, DEAD_REFRESH_TOKEN));
        givenTheTokenIsDead(lineageEnabled);

        filterWithFlag(lineageEnabled).doFilterInternal(request, response, filterChain);

        assertCookieWithdrawn(CookieConstants.ACCESS_TOKEN);
        assertCookieWithdrawn(CookieConstants.REFRESH_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "a dead token must not authenticate the request");
        assertEquals(CustomAuthenticationEntryPoint.SESSION_REVOKED,
                request.getAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE),
                "the client is owed the reason, so it can stop retrying");
    }

    /**
     * Each implementation recognizes a spent token its own way: the lineage state machine reports a
     * revoked family, and the legacy path finds the token on the rotation blacklist.
     */
    private void givenTheTokenIsDead(boolean lineageEnabled) {
        when(tokenValidator.validateRefreshToken(DEAD_REFRESH_TOKEN)).thenReturn(refreshClaims());
        if (lineageEnabled) {
            when(userService.findActiveById(USER_ID)).thenReturn(Optional.of(activeUser()));
            when(refreshRotationService.rotate(DEAD_REFRESH_TOKEN))
                    .thenReturn(new RotationResult.Revoked("fam-revoked"));
        } else {
            when(tokenBlacklistService.isBlacklisted(DEAD_REFRESH_TOKEN)).thenReturn(true);
        }
    }

    private void assertCookieWithdrawn(String name) {
        Cookie cookie = response.getCookie(name);
        assertNotNull(cookie, name + " must be withdrawn, not left with the client");
        assertEquals(0, cookie.getMaxAge(), name + " must be withdrawn, not reissued");
    }

    private JwtAuthenticationFilter filterWithFlag(boolean lineageEnabled) {
        return new JwtAuthenticationFilter(
                tokenValidator, tokenBlacklistService,
                new AccessTokenAuthenticator(tokenValidator, tokenBlacklistService), cookieUtils, userService,
                new AuthDegradationResponder(objectMapper), tokenGenerator, refreshRotationService,
                new LineageRotationFlag(lineageEnabled), jwtProperties);
    }

    private TokenClaims refreshClaims() {
        return new TokenClaims(
                USER_ID, TokenClaims.TYPE_REFRESH, null,
                new Date(), new Date(System.currentTimeMillis() + 604_800_000L),
                "jti-1", "fam-1", null);
    }

    private User activeUser() {
        return User.builder()
                .id(USER_ID)
                .email("user@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + USER_ID)
                .usernameEpithet("W_CORP")
                .usernameSuffix("usr42")
                .build();
    }
}
