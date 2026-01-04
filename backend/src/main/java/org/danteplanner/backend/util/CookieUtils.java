package org.danteplanner.backend.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring bean for HTTP cookie operations.
 * All cookies are created with secure defaults:
 * - HttpOnly: true (prevents JavaScript access)
 * - Secure: configurable (HTTPS only in production)
 * - Path: "/" (available to entire domain)
 * - SameSite: Lax (CSRF protection while allowing external link navigation)
 *
 * <p>SameSite=Lax allows cookies on top-level navigation (clicking links)
 * but blocks them for embedded requests and form POSTs. This is safe because
 * all GET endpoints in this application are read-only.</p>
 */
@Component
public class CookieUtils {

    /**
     * Whether cookies should require HTTPS.
     * Set to false for local development without HTTPS.
     */
    private final boolean secureCookies;

    public CookieUtils(@Value("${cookie.secure:true}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    /**
     * Sets a secure HTTP-only cookie.
     *
     * @param response HTTP response to add cookie to
     * @param name cookie name
     * @param value cookie value
     * @param maxAgeSeconds cookie lifetime in seconds
     */
    public void setCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * Clears a cookie by setting its max age to 0.
     *
     * @param response HTTP response to add cookie to
     * @param name cookie name to clear
     */
    public void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * Retrieves a cookie value from the request.
     *
     * @param request HTTP request containing cookies
     * @param name cookie name to find
     * @return cookie value, or null if not found
     */
    public String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
