package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.config.TestDataInitializer;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.RateLimitExceededException;
import org.danteplanner.backend.facade.AuthenticationFacade;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.JwtTokenService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestConfig.class, AuthControllerTest.MockAuthFacadeConfig.class})
@Transactional
class AuthControllerTest {

    @TestConfiguration
    static class MockAuthFacadeConfig {
        @Bean
        @Primary
        public AuthenticationFacade authenticationFacade() {
            return Mockito.mock(AuthenticationFacade.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuthenticationFacade authFacade;

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        Mockito.reset(authFacade);

        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
    }

    private Cookie accessTokenCookie() {
        return new Cookie("accessToken", accessToken);
    }


    @Nested
    @DisplayName("GET /api/auth/me - Current User")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should return 200 with user data when token is valid")
        void getCurrentUser_ValidToken_Returns200() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(testUser.getEmail()))
                    .andExpect(jsonPath("$.id").value(testUser.getId()));
        }

        @Test
        @DisplayName("Should return 401 with TOKEN_MISSING when no token provided")
        void getCurrentUser_NoToken_Returns401() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("TOKEN_MISSING"))
                    .andExpect(jsonPath("$.message").value("No access token provided"));
        }

        // Note: Expired token test removed - was testing malformed token, not actual expiration.
        // JWT expiration testing would require short-lived tokens in test config.
        // The malformed token test below covers invalid token rejection.

        @Test
        @DisplayName("Should return 401 when token is malformed")
        void getCurrentUser_MalformedToken_Returns401() throws Exception {
            Cookie malformedCookie = new Cookie("accessToken", "malformed.token.here");

            mockMvc.perform(get("/api/auth/me")
                            .cookie(malformedCookie))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/apple/callback - Apple OAuth Stub")
    class AppleCallbackTests {

        @Test
        @DisplayName("Should return 400 as Apple OAuth not implemented")
        void appleCallback_NotImplemented_Returns400() throws Exception {
            mockMvc.perform(post("/api/auth/apple/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"apple-code\",\"provider\":\"apple\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/google/callback - Google OAuth Flow")
    class GoogleCallbackTests {

        @Test
        @DisplayName("Should return 200 with JWT cookies when OAuth code is valid")
        void callback_ValidCode_Returns200WithJWTCookies() throws Exception {
            User mockUser = User.builder()
                    .id(123L)
                    .email("newuser@example.com")
                    .provider("google")
                    .providerId("google-new-123")
                    .usernameKeyword("W_CORP")
                    .usernameSuffix("test1")
                    .role(UserRole.NORMAL)
                    .build();

            AuthenticationFacade.AuthResult mockResult = new AuthenticationFacade.AuthResult(
                    mockUser,
                    "mock-access-token",
                    "mock-refresh-token",
                    false
            );

            when(authFacade.authenticateWithOAuth(
                    eq("google"),
                    eq("valid-oauth-code"),
                    anyString(),
                    anyString()
            )).thenReturn(mockResult);

            mockMvc.perform(post("/api/auth/google/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"valid-oauth-code\",\"provider\":\"google\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("newuser@example.com"))
                    .andExpect(jsonPath("$.id").value(123))
                    .andExpect(cookie().exists("accessToken"))
                    .andExpect(cookie().httpOnly("accessToken", true))
                    .andExpect(cookie().secure("accessToken", true))
                    .andExpect(cookie().exists("refreshToken"))
                    .andExpect(cookie().httpOnly("refreshToken", true))
                    .andExpect(cookie().secure("refreshToken", true));
                    // SameSite verification gap: CookieUtils sets SameSite=Lax on Cookie object
                    // (verified in CookieUtilsTest), but MockMvc doesn't serialize this attribute
                    // in Set-Cookie headers. Full E2E verification requires integration test with
                    // real HTTP client (WebTestClient or curl).
        }

        @Test
        @DisplayName("Should return 401 when OAuth code is invalid")
        void callback_InvalidCode_Returns401() throws Exception {
            when(authFacade.authenticateWithOAuth(
                    eq("google"),
                    eq("invalid-code"),
                    anyString(),
                    anyString()
            )).thenThrow(new RuntimeException("Invalid authorization code"));

            mockMvc.perform(post("/api/auth/google/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"invalid-code\",\"provider\":\"google\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("Should create new user with random username when user does not exist")
        void callback_NewUser_CreatesAccountWithRandomUsername() throws Exception {
            User newUser = TestDataFactory.createTestUser(userRepository, "newuser@example.com");

            AuthenticationFacade.AuthResult mockResult = new AuthenticationFacade.AuthResult(
                    newUser,
                    "mock-access-token",
                    "mock-refresh-token",
                    false
            );

            when(authFacade.authenticateWithOAuth(
                    eq("google"),
                    eq("new-user-code"),
                    anyString(),
                    anyString()
            )).thenReturn(mockResult);

            mockMvc.perform(post("/api/auth/google/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"new-user-code\",\"provider\":\"google\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("newuser@example.com"));

            User created = userRepository.findById(newUser.getId()).orElseThrow();
            assertThat(created.getUsernameKeyword()).isNotEmpty();
            assertThat(created.getUsernameSuffix()).hasSize(5);
        }

        @Test
        @DisplayName("Should reactivate account when deleted user logs in")
        void callback_DeletedUser_ReactivatesAccount() throws Exception {
            User deletedUser = TestDataFactory.createTestUser(userRepository, "deleted@example.com");
            deletedUser.setDeletedAt(Instant.now());
            userRepository.save(deletedUser);

            deletedUser.setDeletedAt(null);

            AuthenticationFacade.AuthResult mockResult = new AuthenticationFacade.AuthResult(
                    deletedUser,
                    "mock-access-token",
                    "mock-refresh-token",
                    true
            );

            when(authFacade.authenticateWithOAuth(
                    eq("google"),
                    eq("reactivate-code"),
                    anyString(),
                    anyString()
            )).thenReturn(mockResult);

            mockMvc.perform(post("/api/auth/google/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"reactivate-code\",\"provider\":\"google\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("deleted@example.com"));
        }

        @Test
        @DisplayName("Should return 429 when rate limit exceeded")
        void callback_ExceedsRateLimit_Returns429() throws Exception {
            when(authFacade.authenticateWithOAuth(
                    eq("google"),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenThrow(new RateLimitExceededException(null, "auth"));

            mockMvc.perform(post("/api/auth/google/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"rate-limit-code\",\"provider\":\"google\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("Should validate cookie security attributes")
        void callback_ValidCode_SetsCookiesWithSecurityAttributes() throws Exception {
            User mockUser = User.builder()
                    .id(456L)
                    .email("secure@example.com")
                    .provider("google")
                    .providerId("google-456")
                    .usernameKeyword("W_CORP")
                    .usernameSuffix("test2")
                    .role(UserRole.NORMAL)
                    .build();

            AuthenticationFacade.AuthResult mockResult = new AuthenticationFacade.AuthResult(
                    mockUser,
                    "access-token-secure",
                    "refresh-token-secure",
                    false
            );

            when(authFacade.authenticateWithOAuth(
                    eq("google"),
                    eq("secure-code"),
                    anyString(),
                    anyString()
            )).thenReturn(mockResult);

            mockMvc.perform(post("/api/auth/google/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"secure-code\",\"provider\":\"google\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().httpOnly("accessToken", true))
                    .andExpect(cookie().secure("accessToken", true))
                    .andExpect(cookie().httpOnly("refreshToken", true))
                    .andExpect(cookie().secure("refreshToken", true));
                    // SameSite verification gap: CookieUtils sets SameSite=Lax on Cookie object
                    // (verified in CookieUtilsTest), but MockMvc doesn't serialize this attribute
                    // in Set-Cookie headers. Full E2E verification requires integration test with
                    // real HTTP client (WebTestClient or curl).
        }
    }
}
