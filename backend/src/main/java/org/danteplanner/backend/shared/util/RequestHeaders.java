package org.danteplanner.backend.shared.util;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;

public final class RequestHeaders {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private RequestHeaders() {
    }

    public static @Nullable String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    public static @Nullable String forwardedFor(HttpServletRequest request) {
        return request.getHeader(X_FORWARDED_FOR);
    }

    public static @Nullable String cfConnectingIp(HttpServletRequest request) {
        return request.getHeader(CF_CONNECTING_IP);
    }

    public static @Nullable String csrfToken(HttpServletRequest request) {
        return request.getHeader(CsrfDoubleSubmitFilter.CSRF_HEADER);
    }
}
