package org.danteplanner.backend.auth.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import org.danteplanner.backend.auth.token.RevocationOutcome;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.user.api.UserRole;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenAuthenticatorTest {

    private static final Long USER_ID = 42L;

    private final TestJwt jwt = new TestJwt();
    private final List<String> blacklistReads = new ArrayList<>();
    private final RecordingTokenBlacklist blacklist = new RecordingTokenBlacklist(jwt.clock()) {
        @Override
        public RevocationOutcome blacklistCheck(String token) {
            blacklistReads.add("blacklistCheck");
            return super.blacklistCheck(token);
        }

        @Override
        public RevocationOutcome invalidationCheck(Long userId, long issuedAt) {
            blacklistReads.add("invalidationCheck");
            return super.invalidationCheck(userId, issuedAt);
        }
    };
    private final AccessTokenAuthenticator authenticator = new AccessTokenAuthenticator(jwt.service(), blacklist);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticate_WhenTheTokenExpired_ReportsExpiredWithoutReadingTheBlacklist() {
        String expired = new TestJwt(jwt.clock().instant().minus(Duration.ofHours(1)))
                .service().generateAccessToken(USER_ID, UserRole.NORMAL);

        assertThat(authenticator.authenticate(expired, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.EXPIRED);
        assertThat(blacklistReads).isEmpty();
    }

    @Test
    void authenticate_WhenTheTokenIsBlacklisted_RevokesWithoutReadingTheUserInvalidation() {
        String token = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);
        blacklist.blacklistToken(token, jwt.service().validateAccessToken(token).expiration());

        assertThat(authenticator.authenticate(token, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.REVOKED);
        assertThat(blacklistReads).containsExactly("blacklistCheck");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"iat", "exp"})
    void authenticate_WhenTheTokenLacksAClaim_RejectsItWithoutReadingTheBlacklist(String claim) {
        Map<String, Object> payload = jwt.accessTokenPayload(USER_ID);
        payload.remove(claim);

        assertThat(authenticator.authenticate(jwt.sign(payload), request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.REJECTED);
        assertThat(blacklistReads).isEmpty();
    }

    @Test
    void authenticate_WhenTheTokenIsLive_AuthenticatesAsItsUser() {
        String token = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);

        assertThat(authenticator.authenticate(token, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.AUTHENTICATED);
        assertThat(blacklistReads).containsExactly("blacklistCheck", "invalidationCheck");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(USER_ID);
    }

    @Test
    void authenticate_WhenTheUserIsInvalidated_RevokesAfterBothReads() {
        String token = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);
        jwt.clock().advance(Duration.ofSeconds(2));
        blacklist.invalidateUserTokens(USER_ID);

        assertThat(authenticator.authenticate(token, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.REVOKED);
        assertThat(blacklistReads).containsExactly("blacklistCheck", "invalidationCheck");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticate_WhenBothChecksAreSkipped_AuthenticatesAfterBothReads() {
        String token = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);
        List<String> skippedReads = new ArrayList<>();
        AccessTokenAuthenticator degraded = new AccessTokenAuthenticator(jwt.service(),
                new RecordingTokenBlacklist(jwt.clock()) {
                    @Override
                    public RevocationOutcome blacklistCheck(String presented) {
                        skippedReads.add("blacklistCheck");
                        return RevocationOutcome.SKIPPED;
                    }

                    @Override
                    public RevocationOutcome invalidationCheck(Long userId, long issuedAt) {
                        skippedReads.add("invalidationCheck");
                        return RevocationOutcome.SKIPPED;
                    }
                });

        assertThat(degraded.authenticate(token, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.AUTHENTICATED);
        assertThat(skippedReads).containsExactly("blacklistCheck", "invalidationCheck");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(USER_ID);
    }
}
