package org.danteplanner.backend.auth.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.user.api.UserRole;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenAuthenticatorTest {

    private static final Long USER_ID = 42L;

    private final TestJwt jwt = new TestJwt();
    private final List<String> blacklistReads = new ArrayList<>();
    private final RecordingTokenBlacklist blacklist = new RecordingTokenBlacklist(jwt.clock()) {
        @Override
        public boolean isBlacklisted(String token) {
            blacklistReads.add("isBlacklisted");
            return super.isBlacklisted(token);
        }

        @Override
        public boolean isUserTokenInvalidated(Long userId, long issuedAt) {
            blacklistReads.add("isUserTokenInvalidated");
            return super.isUserTokenInvalidated(userId, issuedAt);
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
    void verify_WhenTheTokenExpired_ReportsExpiredWithoutReadingTheBlacklist() {
        String expired = new TestJwt(jwt.clock().instant().minus(Duration.ofHours(1)))
                .service().generateAccessToken(USER_ID, UserRole.NORMAL);

        assertThat(authenticator.verify(expired, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.EXPIRED);
        assertThat(blacklistReads).isEmpty();
    }

    @Test
    void verify_WhenTheTokenIsBlacklisted_RevokesWithoutReadingTheUserInvalidation() {
        String token = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);
        blacklist.blacklistToken(token, jwt.service().validateAccessToken(token).expiration());

        assertThat(authenticator.verify(token, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.REVOKED);
        assertThat(blacklistReads).containsExactly("isBlacklisted");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void verify_WhenTheTokenIsLive_AuthenticatesAsItsUser() {
        String token = jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);

        assertThat(authenticator.verify(token, request))
                .isEqualTo(AccessTokenAuthenticator.AccessTokenOutcome.AUTHENTICATED);
        assertThat(blacklistReads).containsExactly("isBlacklisted", "isUserTokenInvalidated");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(USER_ID);
    }
}
