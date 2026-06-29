package org.danteplanner.backend.support;

import jakarta.servlet.http.Cookie;

import org.danteplanner.backend.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.util.CookieConstants;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc support for the double-submit CSRF filter.
 *
 * <p>{@link CsrfDoubleSubmitFilter} rejects every unsafe MockMvc request that lacks a
 * matching {@code csrf} cookie + {@code X-CSRF-Token} header. Apply {@link #withCsrf()}
 * to mutating {@code mockMvc.perform(...)} calls so they pass enforcement; the helper
 * <b>merges</b> the csrf cookie into any existing cookies (auth/device) rather than
 * replacing them.</p>
 */
public final class CsrfMockMvcSupport {

    private static final String CSRF_TOKEN = "test-csrf-token";

    private CsrfMockMvcSupport() {
    }

    /**
     * Attaches a matching {@code csrf} cookie and {@code X-CSRF-Token} header so the
     * request satisfies {@link CsrfDoubleSubmitFilter}. Existing cookies are preserved.
     */
    public static RequestPostProcessor withCsrf() {
        return (MockHttpServletRequest request) -> {
            Cookie[] existing = request.getCookies();
            Cookie csrfCookie = new Cookie(CookieConstants.CSRF, CSRF_TOKEN);
            if (existing == null || existing.length == 0) {
                request.setCookies(csrfCookie);
            } else {
                Cookie[] merged = new Cookie[existing.length + 1];
                System.arraycopy(existing, 0, merged, 0, existing.length);
                merged[existing.length] = csrfCookie;
                request.setCookies(merged);
            }
            request.addHeader(CsrfDoubleSubmitFilter.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
