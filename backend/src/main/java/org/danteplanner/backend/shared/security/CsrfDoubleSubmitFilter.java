package org.danteplanner.backend.shared.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;

/**
 * On an unsafe method the cookie must be one this server minted and the {@code X-CSRF-Token}
 * header must equal it under a constant-time compare.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER = "X-CSRF-Token";

    static final String CSRF_ERROR_CODE = "CSRF_TOKEN_INVALID";

    static final int COOKIE_MAX_AGE_SECONDS = 604800;

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CookieUtils cookieUtils;
    private final ProblemWriter problemWriter;
    private final CsrfTokenService csrfTokenService;

    /**
     * On an ASYNC dispatch (an SSE continuation) the response is already committed, so setting a
     * cookie throws.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ASYNC;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> cookieToken = cookieUtils.getCookieValue(request, CookieConstants.CSRF);
        boolean serverIssued = cookieToken.map(csrfTokenService::isValid).orElse(false);

        if (!serverIssued) {
            cookieUtils.setReadableCookie(
                    response, CookieConstants.CSRF, csrfTokenService.mint(), COOKIE_MAX_AGE_SECONDS);
        }

        if (requiresEnforcement(request)) {
            String headerToken = request.getHeader(CSRF_HEADER);
            boolean echoed = cookieToken.map(cookie -> tokensMatch(cookie, headerToken)).orElse(false);
            if (!serverIssued || !echoed) {
                reject(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresEnforcement(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod());
    }

    private boolean tokensMatch(String cookieToken, String headerToken) {
        if (cookieToken.isEmpty() || headerToken == null || headerToken.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("CSRF validation failed: missing or mismatched X-CSRF-Token");
        problemWriter.write(request, response,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.FORBIDDEN), CSRF_ERROR_CODE,
                        "Missing or invalid CSRF token"),
                new HttpHeaders());
    }
}
