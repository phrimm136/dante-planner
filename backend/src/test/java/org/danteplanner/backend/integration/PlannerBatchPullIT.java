package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.PlannerBatchRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.util.PlannerConstants;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.AuthCookies.performAuthed;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The batch pull answers about the caller's own live planners and nothing else: an id it may not
 * read is absent from the array rather than an error, which is why the array is not positionally
 * aligned with the request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerBatchPullIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String token;
    private Planner owned;
    private Planner deleted;
    private Planner foreign;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "batch-owner@example.com");
        User stranger = TestDataFactory.createTestUser(userRepository, "batch-stranger@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);

        owned = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        deleted = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        deleted.softDelete();
        plannerRepository.save(deleted);
        foreign = TestDataFactory.createTestPlanner(plannerRepository, stranger, false);
    }

    private ResultActions pull(List<UUID> ids) throws Exception {
        return performAuthed(mockMvc, post("/api/planner/md/batch").with(withCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlannerBatchRequest(ids))),
                token);
    }

    private static List<String> idsOf(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(element -> ids.add(element.get("id").asText()));
        return ids;
    }

    @Test
    void batchPull_WhenIdsMixOwnershipAndState_ReturnsOnlyTheOwnedLiveOnes() throws Exception {
        List<UUID> ids = List.of(
                owned.getId(), deleted.getId(), foreign.getId(), UUID.randomUUID());

        String json = pull(ids)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(idsOf(objectMapper.readTree(json)))
                .containsExactly(owned.getId().toString());
    }

    @Test
    void batchPull_WhenIdsExceedTheBound_Returns400() throws Exception {
        List<UUID> ids = Stream.generate(UUID::randomUUID)
                .limit(PlannerConstants.BATCH_PULL_MAX_IDS + 1L)
                .toList();

        pull(ids)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void batchPull_WhenIdsAreEmpty_Returns400() throws Exception {
        pull(List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
