package org.danteplanner.backend.shared.util;

import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SameSite=Lax allows cookies on top-level navigation (clicking links) but blocks them for
 * embedded requests and cross-site form POSTs.
 */
@Component
public class CookieUtils {

    private final boolean secureCookies;

    private final String cookieDomain;

    private final String sameSite;

    public CookieUtils(
            @Value("${cookie.secure:true}") boolean secureCookies,
            @Value("${cookie.domain:}") String cookieDomain,
            @Value("${cookie.same-site:Lax}") String sameSite) {
        this.secureCookies = secureCookies;
        this.cookieDomain = cookieDomain;
        this.sameSite = sameSite;
    }

    public void setCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        response.addCookie(buildCookie(name, value, maxAgeSeconds, true));
    }

    public void setReadableCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        response.addCookie(buildCookie(name, value, maxAgeSeconds, false));
    }

    private Cookie buildCookie(String name, String value, int maxAgeSeconds, boolean httpOnly) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secureCookies);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", sameSite);
        if (!cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }
        return cookie;
    }

    public void clearCookie(HttpServletResponse response, String name) {
        // A browser drops the stored cookie only when the expiring one matches it on
        // domain/path/secure, so the attributes must come from the same builder that set it.
        response.addCookie(buildCookie(name, "", 0, true));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        clearCookie(response, CookieConstants.ACCESS_TOKEN);
        clearCookie(response, CookieConstants.REFRESH_TOKEN);
    }

    public Optional<String> getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return Optional.ofNullable(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
