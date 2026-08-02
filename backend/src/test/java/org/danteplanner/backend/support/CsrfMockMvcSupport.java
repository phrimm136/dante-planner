package org.danteplanner.backend.support;

import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.shared.security.CsrfTokenService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Base64;

/**
 * MockMvc support for the double-submit CSRF filter.
 *
 * <p>{@link CsrfDoubleSubmitFilter} rejects every unsafe MockMvc request that lacks a
 * matching {@code csrf} cookie + {@code X-CSRF-Token} header. Apply {@link #withCsrf()}
 * to mutating {@code mockMvc.perform(...)} calls so they pass enforcement; the helper
 * <b>merges</b> the csrf cookie into any existing cookies (auth/device) rather than
 * replacing them.</p>
 *
 * <p>The token is minted rather than fabricated: the filter admits only values carrying a
 * MAC under the running key, so it must be derived from the same secret the test profiles
 * configure.</p>
 */
public final class CsrfMockMvcSupport {

    /** Mirrors {@code jwt.encryption-key} in application-test.properties and application-it.properties. */
    private static final String TEST_ENCRYPTION_KEY = "E9dzesGJ+MrmIaVssATsvvP9EOc1M/Cj/u+9Bh1WTBg=";

    private static final CsrfTokenService TOKEN_SERVICE = buildTokenService();

    private CsrfMockMvcSupport() {
    }

    private static CsrfTokenService buildTokenService() {
        JwtProperties properties = new JwtProperties();
        properties.setEncryptionKeyBytes(Base64.getDecoder().decode(TEST_ENCRYPTION_KEY));
        return new CsrfTokenService(properties);
    }

    /**
     * Attaches a matching {@code csrf} cookie and {@code X-CSRF-Token} header so the
     * request satisfies {@link CsrfDoubleSubmitFilter}. Existing cookies are preserved.
     */
    public static RequestPostProcessor withCsrf() {
        return (MockHttpServletRequest request) -> {
            String token = TOKEN_SERVICE.mint();
            Cookie[] existing = request.getCookies();
            Cookie csrfCookie = new Cookie(CookieConstants.CSRF, token);
            if (existing == null || existing.length == 0) {
                request.setCookies(csrfCookie);
            } else {
                Cookie[] merged = new Cookie[existing.length + 1];
                System.arraycopy(existing, 0, merged, 0, existing.length);
                merged[existing.length] = csrfCookie;
                request.setCookies(merged);
            }
            request.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, token);
            return request;
        };
    }
}
