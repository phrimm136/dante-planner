package org.danteplanner.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.AccountDeletedException;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.danteplanner.backend.exception.TokenRevokedException;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.danteplanner.backend.service.token.TokenClaims;
import org.danteplanner.backend.service.token.TokenValidator;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/**
 * JWT authentication filter that validates access tokens from cookies.
 * Checks token validity and blacklist status before setting authentication.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;
    private final CookieUtils cookieUtils;
    private final UserService userService;

    public JwtAuthenticationFilter(
            TokenValidator tokenValidator,
            TokenBlacklistService tokenBlacklistService,
            CookieUtils cookieUtils,
            UserService userService
    ) {
        this.tokenValidator = tokenValidator;
        this.tokenBlacklistService = tokenBlacklistService;
        this.cookieUtils = cookieUtils;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Check if token is blacklisted (revoked)
            if (tokenBlacklistService.isBlacklisted(token)) {
                throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
            }

            // Validate token and extract claims (includes expiry check)
            TokenClaims claims = tokenValidator.validateToken(token);
            Long userId = claims.userId();

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Guard sentinel user (id=0) from being authenticated
                if (userId.equals(UserAccountLifecycleService.SENTINEL_USER_ID)) {
                    log.warn("Attempt to authenticate as sentinel user blocked");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Check if user exists and is not deleted
                Optional<User> activeUser = userService.findActiveById(userId);
                if (activeUser.isEmpty()) {
                    // User may be deleted - check if they exist at all
                    try {
                        User user = userService.findById(userId);
                        if (user.isDeleted()) {
                            throw new AccountDeletedException(userId);
                        }
                    } catch (Exception e) {
                        if (e instanceof AccountDeletedException) {
                            throw e;
                        }
                        // User not found at all - token is invalid
                        logSecurityEvent("USER_NOT_FOUND", request);
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (TokenRevokedException e) {
            logSecurityEvent("TOKEN_REVOKED", request);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_REVOKED", e.getMessage());
            return;
        } catch (AccountDeletedException e) {
            logSecurityEvent("ACCOUNT_DELETED", request);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCOUNT_DELETED", e.getMessage());
            return;
        } catch (InvalidTokenException e) {
            logSecurityEvent("INVALID_TOKEN", request);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN", e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Logs security events for audit and attack detection.
     */
    private void logSecurityEvent(String event, HttpServletRequest request) {
        log.warn("Security event: {} - IP: {}, URI: {}, UA: {}",
                event,
                request.getRemoteAddr(),
                request.getRequestURI(),
                request.getHeader("User-Agent"));
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                String.format("{\"error\": \"%s\", \"message\": \"%s\"}", code, message)
        );
    }
}
