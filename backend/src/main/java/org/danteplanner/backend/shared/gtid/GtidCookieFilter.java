package org.danteplanner.backend.shared.gtid;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;

/**
 * Enforces read-your-writes consistency around each request.
 *
 * <p>On safe reads (GET/HEAD) carrying the {@link GtidCookie#NAME} cookie, consults the
 * {@link GtidReadGate}: if the replica has applied the GTID the cookie is cleared, otherwise the
 * request is pinned to the primary for the duration of the chain. On writes it echoes the
 * just-committed GTID from {@link GtidWriteCapture} back into the response cookie.</p>
 */
public class GtidCookieFilter extends OncePerRequestFilter {

    private final GtidReadGate readGate;
    private final GtidWriteCapture writeCapture;

    public GtidCookieFilter(GtidReadGate readGate, GtidWriteCapture writeCapture) {
        this.readGate = readGate;
        this.writeCapture = writeCapture;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isSafeMethod(request)) {
            handleRead(request, response, filterChain);
        } else {
            handleWrite(request, response, filterChain);
        }
    }

    private void handleRead(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String gtid = Optional.ofNullable(readCookie(request))
                .flatMap(GtidCookie::decode)
                .orElse(null);
        if (gtid == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (readGate.isCaughtUp(gtid)) {
            addCookie(response, GtidCookie.cleared());
            filterChain.doFilter(request, response);
            return;
        }
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    private void handleWrite(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        Optional<String> captured = writeCapture.pollCapturedGtid();
        captured.ifPresent(gtid -> addCookie(response, GtidCookie.of(gtid)));
    }

    private boolean isSafeMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (GtidCookie.NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
