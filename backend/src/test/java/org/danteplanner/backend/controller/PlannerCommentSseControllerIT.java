package org.danteplanner.backend.controller;
import org.danteplanner.backend.comment.controller.PlannerCommentSseController;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;

import org.danteplanner.backend.config.TestConfig;
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

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests pinning the wire behavior of {@link PlannerCommentSseController}:
 * the comment feed is public (no authentication) and a subscribe opens a
 * {@code text/event-stream} async response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerCommentSseControllerIT extends SharedMySqlContainerSupport {

    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    /** Distinct from every other class's, so the bucket cannot be spent by a neighbour. */
    private static final String ATTACKER_IP = "198.51.100.61";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    private UUID publishedPlannerId;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "sse-owner@example.com");
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);
        publishedPlannerId = planner.getId();
    }

    @Test
    @DisplayName("opens a text/event-stream for a guest with no authentication")
    void subscribeToComments_WhenNoAuth_StartsEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/planner/{plannerId}/comments/events", publishedPlannerId))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    @DisplayName("opens a text/event-stream when a device cookie is present")
    void subscribeToComments_WhenDeviceCookiePresent_StartsEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/planner/{plannerId}/comments/events", publishedPlannerId)
                        .cookie(AuthCookies.freshDeviceId()))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * A cookie-less caller is issued a freshly minted device id per request, so a bucket keyed by
     * that id is a new bucket every time — an unauthenticated client could fill this planner's
     * connection registry and lock its real subscribers out. The bucket must follow the caller.
     */
    @Test
    @DisplayName("charges one bucket across cookie-less requests from the same client address")
    void subscribeToComments_WhenCookielessRepeatFromSameAddress_ExhaustsOneBucket() throws Exception {
        mockMvc.perform(get("/api/planner/{plannerId}/comments/events", publishedPlannerId)
                        .header(CF_CONNECTING_IP, ATTACKER_IP))
                .andExpect(request().asyncStarted());

        mockMvc.perform(get("/api/planner/{plannerId}/comments/events", publishedPlannerId)
                        .header(CF_CONNECTING_IP, ATTACKER_IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
