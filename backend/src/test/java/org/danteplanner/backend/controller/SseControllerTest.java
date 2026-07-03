package org.danteplanner.backend.controller;
import org.danteplanner.backend.shared.controller.SseController;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests pinning the wire behavior of {@link SseController}:
 * authentication is required, and an authenticated subscribe opens a
 * {@code text/event-stream} async response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User testUser = TestDataFactory.createTestUser(userRepository, "sse-test@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
    }

    @Test
    @DisplayName("returns 401 when unauthenticated")
    void subscribe_WhenUnauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/sse/subscribe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("opens a text/event-stream when authenticated")
    void subscribe_WhenAuthenticated_StartsEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sse/subscribe")
                        .cookie(new Cookie("accessToken", accessToken)))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
