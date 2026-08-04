package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.service.AuthenticationService;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
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


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, AuthControllerIT.MockAuthFacadeConfig.class})
class AuthControllerIT extends SharedMySqlContainerSupport {

    @TestConfiguration
    static class MockAuthFacadeConfig {
        @Bean
        @Primary
        public AuthenticationService authenticationService(
                OAuthProviderRegistry providerRegistry,
                TokenGenerator tokenGenerator,
                TokenValidator tokenValidator,
                TokenBlacklistService tokenBlacklistService,
                UserService userService,
                UserAccountLifecycleService lifecycleService,
                LineageRotationFlag lineageRotationFlag) {
            return Mockito.spy(new AuthenticationService(
                    providerRegistry,
                    tokenGenerator,
                    tokenValidator,
                    tokenBlacklistService,
                    userService,
                    lifecycleService,
                    lineageRotationFlag));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuthenticationService authService;

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        Mockito.reset(authService);


        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }


    @Nested
    @DisplayName("GET /api/auth/me - Current User")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should return 200 with user data when token is valid")
        void getCurrentUser_WhenValidToken_Returns200() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(testUser.getEmail()));
        }

        @Test
        @DisplayName("Should return 204 when no token provided (guest user)")
        void getCurrentUser_WhenNoToken_Returns204() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        // Note: Expired token test removed - was testing malformed token, not actual expiration.
        // JWT expiration testing would require short-lived tokens in test config.
        // The malformed token test below covers invalid token rejection.

        @Test
        @DisplayName("Should return 204 when token is malformed (treat as guest)")
        void getCurrentUser_WhenMalformedToken_Returns204() throws Exception {
            Cookie malformedCookie = AuthCookies.accessToken("malformed.token.here");

            mockMvc.perform(get("/api/auth/me")
                            .cookie(malformedCookie))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/apple/callback - Apple OAuth Stub")
    class AppleCallbackTests {

        @Test
        @DisplayName("Should return 400 as Apple OAuth not implemented")
        void appleCallback_WhenNotImplemented_Returns400() throws Exception {
            mockMvc.perform(post("/api/auth/apple/callback").with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"apple-code\",\"provider\":\"apple\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
