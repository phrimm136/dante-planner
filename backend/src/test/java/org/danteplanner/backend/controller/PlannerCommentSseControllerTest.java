package org.danteplanner.backend.controller;
import org.danteplanner.backend.comment.controller.PlannerCommentSseController;

import jakarta.servlet.http.Cookie;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Characterization tests pinning the wire behavior of {@link PlannerCommentSseController}:
 * the comment feed is public (no authentication) and a subscribe opens a
 * {@code text/event-stream} async response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class PlannerCommentSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("opens a text/event-stream for a guest with no authentication")
    void subscribeToComments_WhenNoAuth_StartsEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/planner/{plannerId}/comments/events", UUID.randomUUID()))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    @DisplayName("opens a text/event-stream when a device cookie is present")
    void subscribeToComments_WhenDeviceCookiePresent_StartsEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/planner/{plannerId}/comments/events", UUID.randomUUID())
                        .cookie(new Cookie("deviceId", UUID.randomUUID().toString())))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
