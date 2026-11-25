package org.danteplanner.backend.shared.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.backend.shared.security.CsrfTokenService.TokenOutcome;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.shared.util.LogValues;
import org.danteplanner.backend.shared.util.RequestHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.kv;

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

    /** INVALID_COOKIE covers a bad MAC and a token past its lifetime alike. */
    private enum Verdict {
        ACCEPTED, NO_COOKIE, INVALID_COOKIE, NO_HEADER, MISMATCH;

        static Verdict of(String method, TokenOutcome cookie, @Nullable String headerToken) {
            if (SAFE_METHODS.contains(method)) {
                return ACCEPTED;
            }
            return switch (cookie) {
                case TokenOutcome.Absent absent -> NO_COOKIE;
                case TokenOutcome.Unrecognized unrecognized -> INVALID_COOKIE;
                case TokenOutcome.ServerIssued issued -> ofEcho(issued.value(), headerToken);
            };
        }

        private static Verdict ofEcho(String cookieToken, @Nullable String headerToken) {
            if (headerToken == null || headerToken.isEmpty()) {
                return NO_HEADER;
            }
            boolean echoed = MessageDigest.isEqual(
                    cookieToken.getBytes(StandardCharsets.UTF_8),
                    headerToken.getBytes(StandardCharsets.UTF_8));
            return echoed ? ACCEPTED : MISMATCH;
        }
    }

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
        TokenOutcome cookie = cookieUtils.getCookieValue(request, CookieConstants.CSRF)
                .map(csrfTokenService::parse)
                .orElseGet(TokenOutcome.Absent::new);
        switch (cookie) {
            case TokenOutcome.ServerIssued issued -> { }
            case TokenOutcome.Unrecognized unrecognized -> issueCookie(response);
            case TokenOutcome.Absent absent -> issueCookie(response);
        }

        Verdict verdict = Verdict.of(request.getMethod(), cookie, RequestHeaders.csrfToken(request));
        switch (verdict) {
            case ACCEPTED -> filterChain.doFilter(request, response);
            case NO_COOKIE, INVALID_COOKIE, NO_HEADER, MISMATCH -> reject(verdict, request, response);
        }
    }

    private void issueCookie(HttpServletResponse response) {
        cookieUtils.setReadableCookie(
                response, CookieConstants.CSRF, csrfTokenService.mint(), COOKIE_MAX_AGE_SECONDS);
    }

    private void reject(Verdict verdict, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("CSRF validation failed: {} {} {} {}",
                kv("reason", verdict),
                kv("method", request.getMethod()),
                kv("path", LogValues.oneLine(request.getRequestURI())),
                kv("userAgent", LogValues.oneLine(RequestHeaders.userAgent(request))));
        problemWriter.write(request, response,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.FORBIDDEN), CSRF_ERROR_CODE,
                        "Missing or invalid CSRF token"),
                new HttpHeaders());
    }
}
