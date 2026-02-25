package org.danteplanner.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.AccountDeletedException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.danteplanner.backend.service.token.TokenClaims;
import org.danteplanner.backend.service.token.TokenGenerator;
import org.danteplanner.backend.service.token.TokenValidator;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.danteplanner.backend.exception.InvalidTokenException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * <p>Tests token validation, blacklist checking, deleted user rejection,
 * and sentinel user protection.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private CookieUtils cookieUtils;

    @Mock
    private UserService userService;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new JwtAuthenticationFilter(tokenValidator, tokenBlacklistService, cookieUtils, userService, objectMapper, tokenGenerator);
        SecurityContextHolder.clearContext();
        // doFilterInternal now reads method+path at the top for MDC — stub to avoid NPE
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");
    }

    private TokenClaims createValidClaims(Long userId) {
        return new TokenClaims(
                userId,
                "test@example.com",
                TokenClaims.TYPE_ACCESS,
                UserRole.NORMAL,
                new Date(),
                new Date(System.currentTimeMillis() + 3600000)
        );
    }

    private User createActiveUser(Long id) {
        return User.builder()
                .id(id)
                .email("test@example.com")
                .provider("google")
                .providerId("google-" + id)
                .usernameEpithet("W_CORP")
                .usernameSuffix("tst" + String.format("%02d", id % 100))
                .build();
    }

    private User createDeletedUser(Long id) {
        User user = createActiveUser(id);
        user.softDelete(Instant.now().plus(Duration.ofDays(30)));
        return user;
    }

    @Nested
    @DisplayName("Deleted User Rejection Tests")
    class DeletedUserRejectionTests {

        @Test
        @DisplayName("Should clear SecurityContext for deleted user and continue filter chain")
        void doFilterInternal_deletedUser_clearsContextAndContinues() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;
            User deletedUser = createDeletedUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.empty());
            when(userService.findById(userId)).thenReturn(deletedUser);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - SecurityContext cleared, filter continues (will get 401 from SecurityConfig)
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared for deleted users");
        }
    }

    @Nested
    @DisplayName("Sentinel User Protection Tests")
    class SentinelUserProtectionTests {

        @Test
        @DisplayName("Should skip authentication for sentinel user (id=0)")
        void doFilterInternal_sentinelUser_skipsAuthentication() throws Exception {
            // Arrange
            String token = "sentinel.jwt.token";
            Long sentinelId = 0L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(sentinelId));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(userService, never()).findActiveById(any());
        }
    }

    @Nested
    @DisplayName("Active User Authentication Tests")
    class ActiveUserAuthenticationTests {

        @Test
        @DisplayName("Should set authentication for active user")
        void doFilterInternal_activeUser_setsAuthentication() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;
            User activeUser = createActiveUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.of(activeUser));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        }

        @Test
        @DisplayName("Should continue filter chain after successful authentication")
        void doFilterInternal_activeUser_continuesFilterChain() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;
            User activeUser = createActiveUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.of(activeUser));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("No Token Tests")
    class NoTokenTests {

        @Test
        @DisplayName("Should continue without authentication when no token present")
        void doFilterInternal_noToken_continuesWithoutAuth() throws Exception {
            // Arrange
            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(null);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(tokenValidator, never()).validateToken(any());
        }
    }

    @Nested
    @DisplayName("User Not Found Tests")
    class UserNotFoundTests {

        @Test
        @DisplayName("Should continue without auth when user not found at all")
        void doFilterInternal_userNotFound_continuesWithoutAuth() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.empty());
            when(userService.findById(userId)).thenThrow(new UserNotFoundException(userId));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Nested
    @DisplayName("Invalid Token Handling Tests")
    class InvalidTokenHandlingTests {

        @Test
        @DisplayName("Should attempt auto-refresh for expired token")
        void doFilterInternal_expiredToken_attemptsAutoRefresh() throws Exception {
            // Arrange
            String expiredToken = "expired.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(expiredToken);
            when(cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)).thenReturn(null); // No refresh token
            when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - auto-refresh attempted but failed (no refresh token)
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        @DisplayName("Should clear SecurityContext for malformed token")
        void doFilterInternal_malformedToken_clearsContext() throws Exception {
            // Arrange
            String token = "malformed.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - no auto-refresh for malformed tokens
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        @DisplayName("Should clear SecurityContext for invalid signature")
        void doFilterInternal_invalidSignature_clearsContext() throws Exception {
            // Arrange
            String token = "tampered.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - no auto-refresh for tampered tokens
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }

        @Test
        @DisplayName("Should clear SecurityContext for revoked token")
        void doFilterInternal_revokedToken_clearsContext() throws Exception {
            // Arrange
            String token = "revoked.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.REVOKED));

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - no auto-refresh for invalid type
            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext should be cleared");
        }
    }

}
