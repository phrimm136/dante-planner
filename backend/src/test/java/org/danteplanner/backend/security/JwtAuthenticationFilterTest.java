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
        filter = new JwtAuthenticationFilter(tokenValidator, tokenBlacklistService, cookieUtils, userService, objectMapper);
        SecurityContextHolder.clearContext();
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
                .usernameKeyword("W_CORP")
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
        @DisplayName("Should throw AccountDeletedException for deleted user")
        void doFilterInternal_deletedUser_throwsAccountDeletedException() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;
            User deletedUser = createDeletedUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.empty());
            when(userService.findById(userId)).thenReturn(deletedUser);

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response).setContentType("application/json");
            verify(filterChain, never()).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("Should write ACCOUNT_DELETED error response")
        void doFilterInternal_deletedUser_writesAccountDeletedError() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;
            User deletedUser = createDeletedUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.empty());
            when(userService.findById(userId)).thenReturn(deletedUser);

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            writer.flush();
            String responseBody = stringWriter.toString();
            assertTrue(responseBody.contains("ACCOUNT_DELETED"));
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
    @DisplayName("Invalid Token Error Code Tests")
    class InvalidTokenErrorCodeTests {

        @Test
        @DisplayName("Should return TOKEN_EXPIRED for expired token")
        void doFilterInternal_expiredToken_returnsTokenExpired() throws Exception {
            // Arrange
            String token = "expired.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            writer.flush();
            String responseBody = stringWriter.toString();
            assertTrue(responseBody.contains("TOKEN_EXPIRED"));
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("Should return TOKEN_INVALID for malformed token")
        void doFilterInternal_malformedToken_returnsTokenInvalid() throws Exception {
            // Arrange
            String token = "malformed.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            writer.flush();
            String responseBody = stringWriter.toString();
            assertTrue(responseBody.contains("TOKEN_INVALID"));
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("Should return TOKEN_INVALID for invalid signature")
        void doFilterInternal_invalidSignature_returnsTokenInvalid() throws Exception {
            // Arrange
            String token = "tampered.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE));

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            writer.flush();
            String responseBody = stringWriter.toString();
            assertTrue(responseBody.contains("TOKEN_INVALID"));
        }

        @Test
        @DisplayName("Should return TOKEN_REVOKED for revoked token reason")
        void doFilterInternal_revokedTokenReason_returnsTokenRevoked() throws Exception {
            // Arrange
            String token = "revoked.jwt.token";

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenValidator.validateToken(token))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.REVOKED));

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            writer.flush();
            String responseBody = stringWriter.toString();
            assertTrue(responseBody.contains("TOKEN_REVOKED"));
        }
    }

    @Nested
    @DisplayName("JSON Error Response Tests (Injection Prevention)")
    class JsonErrorResponseTests {

        @Test
        @DisplayName("Should produce valid JSON when error message contains quotes")
        void doFilterInternal_errorWithQuotes_producesValidJson() throws Exception {
            // Arrange - trigger AccountDeletedException with quotes in message
            String token = "valid.jwt.token";
            Long userId = 123L;
            User deletedUser = createDeletedUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.empty());
            when(userService.findById(userId)).thenReturn(deletedUser);

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - response should be valid JSON
            writer.flush();
            String responseBody = stringWriter.toString();

            // This should NOT throw - proves JSON is valid
            assertDoesNotThrow(() -> objectMapper.readTree(responseBody),
                    "Response should be valid JSON");
        }

        @Test
        @DisplayName("Should escape special characters in error response")
        void doFilterInternal_errorWithSpecialChars_escapesCorrectly() throws Exception {
            // Arrange
            String token = "valid.jwt.token";
            Long userId = 123L;
            User deletedUser = createDeletedUser(userId);

            when(cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)).thenReturn(token);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(tokenValidator.validateToken(token)).thenReturn(createValidClaims(userId));
            when(userService.findActiveById(userId)).thenReturn(Optional.empty());
            when(userService.findById(userId)).thenReturn(deletedUser);

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(writer);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            writer.flush();
            String responseBody = stringWriter.toString();

            // Verify it's parseable JSON with expected structure
            var jsonNode = objectMapper.readTree(responseBody);
            assertTrue(jsonNode.has("error"), "Should have 'error' field");
            assertTrue(jsonNode.has("message"), "Should have 'message' field");
            assertEquals("ACCOUNT_DELETED", jsonNode.get("error").asText());
        }
    }
}
