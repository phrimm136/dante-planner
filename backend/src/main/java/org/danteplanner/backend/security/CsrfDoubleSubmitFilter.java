package org.danteplanner.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Self-enforcing CSRF protection via the double-submit cookie pattern.
 *
 * <p>Two responsibilities run per request, before {@code JwtAuthenticationFilter}:</p>
 * <ol>
 *   <li><b>Ensure-cookie:</b> if no readable {@code csrf} cookie is present, a fresh
 *       128-bit+ random token is minted and set on the response. Browsers load the SPA
 *       with a GET first, so they always obtain a token before any mutation.</li>
 *   <li><b>Enforce:</b> for unsafe methods (POST/PUT/PATCH/DELETE) the request must carry an
 *       {@code X-CSRF-Token} header equal to the {@code csrf} cookie (constant-time compare),
 *       or the request is rejected with 403 and the chain is not continued.</li>
 * </ol>
 *
 * <p>Safe methods (GET/HEAD/OPTIONS) and machine-to-machine {@code /api/internal/**}
 * endpoints (own API-key gate, no browser origin) are exempt from enforcement.</p>
 *
 * @see <a href="https://owasp.org/www-community/attacks/csrf">OWASP CSRF</a>
 */
@Component
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
     * Token entropy in bytes (256-bit, well above the 128-bit minimum).
     */
    static final int TOKEN_BYTE_LENGTH = 32;

    /**
     * Lifetime of the {@code csrf} cookie in seconds (7 days), matching the refresh window.
     */
    static final int COOKIE_MAX_AGE_SECONDS = 604800;

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private final CookieUtils cookieUtils;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    public CsrfDoubleSubmitFilter(CookieUtils cookieUtils, ObjectMapper objectMapper) {
        this.cookieUtils = cookieUtils;
        this.objectMapper = objectMapper;
    }

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
        String cookieToken = cookieUtils.getCookieValue(request, CookieConstants.CSRF);

        // Ensure-cookie: guarantee the browser holds a token before it can mutate.
        // Runs before enforcement, so a guest's first mutation receives a Set-Cookie
        // and is still rejected (the request-side cookie is still absent).
        if (cookieToken == null || cookieToken.isEmpty()) {
            cookieUtils.setReadableCookie(
                    response, CookieConstants.CSRF, generateToken(), COOKIE_MAX_AGE_SECONDS);
        }

        if (requiresEnforcement(request)) {
            String headerToken = request.getHeader(CSRF_HEADER);
            if (!tokensMatch(cookieToken, headerToken)) {
                reject(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresEnforcement(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    private boolean tokensMatch(String cookieToken, String headerToken) {
        if (cookieToken == null || cookieToken.isEmpty()
                || headerToken == null || headerToken.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }

    private void reject(HttpServletResponse response) throws IOException {
        log.warn("CSRF validation failed: missing or mismatched X-CSRF-Token");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", CSRF_ERROR_CODE, "message", "Missing or invalid CSRF token")));
    }
}
