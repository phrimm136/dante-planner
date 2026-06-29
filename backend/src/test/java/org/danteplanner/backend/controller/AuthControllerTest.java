package org.danteplanner.backend.controller;

import org.danteplanner.backend.entity.AuthProviderType;
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
import java.util.UUID;

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
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

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
                    .andExpect(jsonPath("$.email").value(testUser.getEmail()));
        }

        @Test
        @DisplayName("Should return 200 with null body when no token provided (guest user)")
        void getCurrentUser_NoToken_Returns200WithNull() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").doesNotExist());
        }

        // Note: Expired token test removed - was testing malformed token, not actual expiration.
        // JWT expiration testing would require short-lived tokens in test config.
        // The malformed token test below covers invalid token rejection.

        @Test
        @DisplayName("Should return 200 with null body when token is malformed (treat as guest)")
        void getCurrentUser_MalformedToken_Returns200WithNull() throws Exception {
            Cookie malformedCookie = new Cookie("accessToken", "malformed.token.here");

            mockMvc.perform(get("/api/auth/me")
                            .cookie(malformedCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/apple/callback - Apple OAuth Stub")
    class AppleCallbackTests {

        @Test
        @DisplayName("Should return 400 as Apple OAuth not implemented")
        void appleCallback_NotImplemented_Returns400() throws Exception {
            mockMvc.perform(post("/api/auth/apple/callback").with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"apple-code\",\"provider\":\"apple\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
