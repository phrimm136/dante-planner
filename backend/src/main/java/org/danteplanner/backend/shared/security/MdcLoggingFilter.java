package org.danteplanner.backend.shared.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.danteplanner.backend.shared.util.LogValues;

import java.io.IOException;

@Component
@Slf4j
public class MdcLoggingFilter extends OncePerRequestFilter {

    /**
     * MDC is thread-local; async continuations run on a different thread, where the MDC is
     * already empty.
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
        MDC.put("userId", switch (SpringContextAdapter.viewer()) {
            case Viewer.Member member -> String.valueOf(member.userId());
            case Viewer.Guest guest -> "guest";
        });
        MDC.put("method", request.getMethod());
        MDC.put("path", LogValues.oneLine(request.getRequestURI()));

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
