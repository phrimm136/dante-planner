package org.danteplanner.backend.support;

import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.shared.util.CookieConstants;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

/**
 * Cookie factories for the MockMvc suite.
 *
 * <p>The names come from {@link CookieConstants}, the same source the filter chain reads them
 * from, so a rename cannot leave a test asserting against a cookie the server no longer looks
 * for.</p>
 */
public final class AuthCookies {

    private AuthCookies() {
    }

    /** The cookie {@code JwtAuthenticationFilter} authenticates a request from. */
    public static Cookie accessToken(String token) {
        return new Cookie(CookieConstants.ACCESS_TOKEN, token);
    }

    /** The cookie the refresh and logout endpoints read the rotating token from. */
    public static Cookie refreshToken(String token) {
        return new Cookie(CookieConstants.REFRESH_TOKEN, token);
    }

    public static Cookie deviceId(String deviceId) {
        return new Cookie(CookieConstants.DEVICE_ID, deviceId);
    }

    public static Cookie deviceId(UUID deviceId) {
        return deviceId(deviceId.toString());
    }

    /** A device id no other request in the suite will send. */
    public static Cookie freshDeviceId() {
        return deviceId(UUID.randomUUID());
    }

    /** The access-token and device-id pair a browser session carries. */
    public static Cookie[] session(String accessToken, UUID deviceId) {
        return new Cookie[] {accessToken(accessToken), deviceId(deviceId)};
    }

    /**
     * Performs the request as the holder of {@code token}.
     *
     * @param mockMvc the entry point under test
     * @param request the request to authenticate, already carrying its own body and processors
     * @param token   the access token to send
     * @return the performed request's result actions
     * @throws Exception whatever {@link MockMvc#perform} raises
     */
    public static ResultActions performAuthed(
            MockMvc mockMvc, MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.cookie(accessToken(token)));
    }
}
