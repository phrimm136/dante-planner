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
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.MarkerStore;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

public class GtidCookieFilter extends OncePerRequestFilter {

    private final GtidReadGate readGate;
    private final GtidWriteCapture writeCapture;
    private final MarkerStore markerStore;
    private final ProblemWriter problemWriter;

    public GtidCookieFilter(GtidReadGate readGate, GtidWriteCapture writeCapture, MarkerStore markerStore,
            ProblemWriter problemWriter) {
        this.readGate = readGate;
        this.writeCapture = writeCapture;
        this.markerStore = markerStore;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        writeCapture.begin();
        FreshnessDeclarations.begin();
        ChosenReadSource.clear();
        try {
            if (isSafeMethod(request)) {
                handleRead(request, response, filterChain);
            } else {
                filterChain.doFilter(request, response);
            }
            // A bodied response is already committed by the converter's flush.
            writeCapture.takeCapturedGtid().ifPresent(gtid -> {
                markerStore.raise(FreshnessDeclarations.declared(), gtid);
                addCookie(response, GtidCookie.of(gtid));
            });
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
            ChosenReadSource.clear();
        }
    }

    private void handleRead(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String cookieValue = readCookie(request);
        Optional<String> gtid = GtidCookie.decode(cookieValue);
        if (readGate.readsAuthority()) {
            if (gtid.isPresent()) {
                readOnPrimary(request, response, filterChain);
            } else {
                filterChain.doFilter(request, response);
            }
            return;
        }
        if (gtid.isEmpty()) {
            if (GtidCookie.isUnusable(cookieValue)) {
                addCookie(response, GtidCookie.cleared());
            }
            filterChain.doFilter(request, response);
            return;
        }
        GtidReadGate.Verdict verdict = readGate.probe(gtid.get());
        if (verdict != GtidReadGate.Verdict.NOT_CAUGHT_UP) {
            addCookie(response, GtidCookie.cleared());
        }
        if (verdict == GtidReadGate.Verdict.CAUGHT_UP || verdict == GtidReadGate.Verdict.UNUSABLE) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!readGate.isAuthorityHealthy()) {
            ReadTemporarilyUnavailableException unavailable = new ReadTemporarilyUnavailableException();
            problemWriter.write(request, response, unavailable.getBody(), unavailable.getHeaders());
            return;
        }
        readOnPrimary(request, response, filterChain);
    }

    private void readOnPrimary(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
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
