package org.danteplanner.backend.controller;

import java.time.Clock;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.controller.AuthController;
import org.danteplanner.backend.auth.service.SessionRevocationService;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.RecordingTokenBlacklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthControllerTest {

    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(Clock.systemUTC());

    @Test
    void logoutAll_WhenThePrincipalIsNull_ThrowsBeforeAnyRevocation() {
        AuthController controller = new AuthController(null,
                new SessionRevocationService(null, tokenBlacklist, null), null, null,
                new CookieUtils(false, "", "Lax"), null, null, null, null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.logoutAll(null, new MockHttpServletRequest(), response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("logout-all was called without an authenticated principal");

        assertThat(tokenBlacklist.userInvalidationSize()).isZero();
        assertThat(tokenBlacklist.failed()).isEmpty();
        assertThat(response.getCookies()).isEmpty();
    }
}
