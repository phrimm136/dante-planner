package org.danteplanner.backend.integration.consistency;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

public final class CookieJar {

    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public CookieJar(Cookie... initial) {
        for (Cookie cookie : initial) {
            cookies.put(cookie.getName(), cookie);
        }
    }

    public void apply(List<String> setCookieHeaders) {
        for (String header : setCookieHeaders) {
            for (HttpCookie cookie : HttpCookie.parse(header)) {
                if (cookie.getMaxAge() == 0) {
                    cookies.remove(cookie.getName());
                } else {
                    cookies.put(cookie.getName(), new Cookie(cookie.getName(), cookie.getValue()));
                }
            }
        }
    }

    public Cookie[] cookies() {
        return cookies.values().toArray(Cookie[]::new);
    }

    public Cookie[] cookiesExcept(String name) {
        return cookies.values().stream()
                .filter(cookie -> !cookie.getName().equals(name))
                .toArray(Cookie[]::new);
    }
}
