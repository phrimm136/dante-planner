package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(TestConfig.class)
class AuthControllerIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }


    @Nested
    class GetCurrentUserTests {

        @Test
        void getCurrentUser_WhenValidToken_Returns200() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(testUser.getEmail()));
        }

        @Test
        void getCurrentUser_WhenNoToken_Returns204() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        // Note: Expired token test removed - was testing malformed token, not actual expiration.
        // JWT expiration testing would require short-lived tokens in test config.
        // The malformed token test below covers invalid token rejection.

        @Test
        void getCurrentUser_WhenMalformedToken_Returns204() throws Exception {
            Cookie malformedCookie = AuthCookies.accessToken("malformed.token.here");

            mockMvc.perform(get("/api/auth/me")
                            .cookie(malformedCookie))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    class AppleCallbackTests {

        @Test
        void appleCallback_WhenNotImplemented_Returns400() throws Exception {
            mockMvc.perform(post("/api/auth/apple/callback").with(withCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"apple-code\",\"provider\":\"apple\",\"codeVerifier\":\"verifier\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
