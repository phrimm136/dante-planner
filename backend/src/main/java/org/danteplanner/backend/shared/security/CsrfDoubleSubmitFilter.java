package org.danteplanner.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Self-enforcing CSRF protection via the double-submit cookie pattern.
 *
 * <p>Two responsibilities run per request, before {@code JwtAuthenticationFilter}:</p>
 * <ol>
 *   <li><b>Ensure-cookie:</b> if the request carries no cookie this server minted, a fresh
 *       keyed token replaces whatever was there. Browsers load the SPA with a GET first,
 *       so they always obtain a token before any mutation.</li>
 *   <li><b>Enforce:</b> for unsafe methods (POST/PUT/PATCH/DELETE) the cookie must be one
 *       this server minted and the {@code X-CSRF-Token} header must equal it (constant-time
 *       compare), or the request is rejected with 403 and the chain is not continued.</li>
 * </ol>
 *
 * <p>Safe methods (GET/HEAD/OPTIONS) are exempt from enforcement.</p>
 *
 * @see <a href="https://owasp.org/www-community/attacks/csrf">OWASP CSRF</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    /**
     * HTTP header the SPA echoes the {@code csrf} cookie value back in.
     */
    public static final String CSRF_HEADER = "X-CSRF-Token";

    /**
     * Error code returned when CSRF validation fails.
     */
    static final String CSRF_ERROR_CODE = "CSRF_TOKEN_INVALID";

    /**
     * Lifetime of the {@code csrf} cookie in seconds (7 days), matching the refresh window.
     */
    static final int COOKIE_MAX_AGE_SECONDS = 604800;

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CookieUtils cookieUtils;
    private final ObjectMapper objectMapper;
    private final CsrfTokenService csrfTokenService;

    /**
     * Skip ASYNC dispatch (SSE continuations): the response is already committed there,
     * so setting a cookie would throw. The initial request already ran this filter.
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

        // Ensure-cookie: guarantee the browser holds a token before it can mutate.
        // Runs before enforcement, so a guest's first mutation receives a Set-Cookie
        // and is still rejected (the request-side cookie is still absent). A cookie
        // that fails verification is replaced rather than left in place, which is what
        // carries browsers holding a token minted before this scheme.
        if (!serverIssued) {
            cookieUtils.setReadableCookie(
                    response, CookieConstants.CSRF, csrfTokenService.mint(), COOKIE_MAX_AGE_SECONDS);
        }

        if (requiresEnforcement(request)) {
            String headerToken = request.getHeader(CSRF_HEADER);
            boolean echoed = cookieToken.map(cookie -> tokensMatch(cookie, headerToken)).orElse(false);
            if (!serverIssued || !echoed) {
                reject(response);
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

    private void reject(HttpServletResponse response) throws IOException {
        log.warn("CSRF validation failed: missing or mismatched X-CSRF-Token");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", CSRF_ERROR_CODE, "message", "Missing or invalid CSRF token")));
    }
}
