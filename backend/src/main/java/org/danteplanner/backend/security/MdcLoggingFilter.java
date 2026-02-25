package org.danteplanner.backend.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates SLF4J MDC with per-request context so every WARN/ERROR log entry
 * automatically carries userId, method, and path.
 * Must run after JwtAuthenticationFilter so SecurityContext is populated.
 */
@Component
@Slf4j
public class MdcLoggingFilter extends OncePerRequestFilter {

    /**
     * Skip MDC population for ASYNC dispatch (SSE continuations).
     * MDC is thread-local; async continuations run on a different thread
     * where the MDC is already empty.
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            MDC.put("userId", String.valueOf(authentication.getPrincipal()));
        } else {
            MDC.put("userId", "guest");
        }
        MDC.put("method", request.getMethod());
        // Strip CR/LF to prevent log injection via crafted URIs in the console PatternLayout
        MDC.put("path", request.getRequestURI().replaceAll("[\r\n]", "_"));

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
