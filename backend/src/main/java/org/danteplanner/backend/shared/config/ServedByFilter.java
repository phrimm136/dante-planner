package org.danteplanner.backend.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with the region that served it.
 */
@Component
public class ServedByFilter extends OncePerRequestFilter {

    static final String SERVED_BY_HEADER = "X-Served-By";

    private final String region;

    public ServedByFilter(@Value("${deploy.region}") String region) {
        this.region = region;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Set before the chain: an SSE response commits as soon as it starts streaming, and headers
        // added after commit are silently dropped.
        response.setHeader(SERVED_BY_HEADER, region);
        filterChain.doFilter(request, response);
    }
}
