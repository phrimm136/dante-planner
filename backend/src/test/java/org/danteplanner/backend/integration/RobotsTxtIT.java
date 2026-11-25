package org.danteplanner.backend.integration;

import java.util.List;
import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RobotsTxtIT extends SharedMySqlContainerSupport {

    private static final String ROBOTS_TXT_PATH = "/robots.txt";
    private static final String COMMENT = "#";
    private static final List<String> RULES = List.of(
            "User-agent: *",
            "Disallow: /api/auth/google/",
            "Disallow: /api/auth/apple/",
            "Disallow: /api/admin/",
            "Disallow: /api/moderation/",
            "Disallow: /api/sse/",
            "Disallow: /api/planner/*/comments/events",
            "Disallow: /actuator/");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void robotsTxt_WhenAnonymousGet_ReturnsTheRules() throws Exception {
        String body = mockMvc.perform(get(ROBOTS_TXT_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        assertThat(body.lines().filter(line -> !line.startsWith(COMMENT)))
                .containsExactlyElementsOf(RULES);
    }

    @Test
    void robotsTxt_WhenAnonymousPost_IsRefused() throws Exception {
        mockMvc.perform(post(ROBOTS_TXT_PATH))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void robotsTxtSibling_WhenAnonymousGet_Returns401() throws Exception {
        mockMvc.perform(get(ROBOTS_TXT_PATH + ".bak"))
                .andExpect(status().isUnauthorized());
    }
}
