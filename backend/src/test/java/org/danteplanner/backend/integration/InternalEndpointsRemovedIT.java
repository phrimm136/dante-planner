package org.danteplanner.backend.integration;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Holds the removed {@code /api/internal} surface closed at the HTTP level: a real request
 * through the full security filter chain reaches no handler and yields 404. Complements the
 * source-scan in {@code InternalSurfaceRemovedTest}, which cannot observe routing.
 *
 * <p>Requests are authenticated (and CSRF-cleared for unsafe methods) so they clear the
 * pre-dispatch filters — the surface's old {@code permitAll} and CSRF exemptions are gone, so an
 * anonymous probe would stop at 401 before routing could prove the handlers no longer exist.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class InternalEndpointsRemovedIT extends SharedMySqlContainerSupport {

    /** The paths the deleted InternalController used to serve. */
    private static final String REFRESH_GAME_DATA_PATH = "/api/internal/refresh-game-data";
    private static final String LINEAGE_ROTATION_PATH = "/api/internal/feature-flags/lineage-rotation";


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private Cookie auth;
    private Cookie device;

    @BeforeEach
    void setUp() {
        User user = TestDataFactory.createTestUser(
                userRepository, "internal-removed-" + UUID.randomUUID() + "@example.com");
        auth = new Cookie("accessToken", TestDataFactory.generateAccessToken(jwtTokenService, user));
        device = new Cookie("deviceId", UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("internalEndpointsRemoved_WhenGetRequested_Returns404")
    void internalEndpointsRemoved_WhenGetRequested_Returns404() throws Exception {
        mockMvc.perform(get(REFRESH_GAME_DATA_PATH).cookie(auth, device))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(LINEAGE_ROTATION_PATH).cookie(auth, device))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("internalEndpointsRemoved_WhenPostRequestedWithCsrf_Returns404")
    void internalEndpointsRemoved_WhenPostRequestedWithCsrf_Returns404() throws Exception {
        mockMvc.perform(post(REFRESH_GAME_DATA_PATH).with(withCsrf()).cookie(auth, device))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(LINEAGE_ROTATION_PATH).with(withCsrf()).cookie(auth, device))
                .andExpect(status().isNotFound());
    }
}
