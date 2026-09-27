package org.danteplanner.backend.shared.config;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ServedByFilterIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Value("${deploy.region}")
    private String region;

    @Test
    void servedBy_WhenUnauthenticated_RidesOnThe401() throws Exception {
        mockMvc.perform(get("/api/planner/md"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().stringValues(ServedByFilter.SERVED_BY_HEADER, contains(region)));
    }

    @Test
    void servedBy_WhenCsrfTokenMissing_RidesOnThe403() throws Exception {
        mockMvc.perform(post("/api/planner/md")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().stringValues(ServedByFilter.SERVED_BY_HEADER, contains(region)));
    }

    @Test
    void servedBy_WhenRequestSucceeds_AppearsExactlyOnce() throws Exception {
        mockMvc.perform(get("/api/planner/md/recommended"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(ServedByFilter.SERVED_BY_HEADER, contains(region)));
    }
}
