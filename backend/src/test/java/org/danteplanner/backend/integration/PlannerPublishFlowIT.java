package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.LegacyPublishRequest;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publish flow over the projections: an owner's title edit on a published
 * planner is visible in the list and detail from the same request on, and
 * publishing is one content-carrying request that upserts and sets published
 * atomically. Unpublish stays a bodyless toggle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerPublishFlowIT {

    @DynamicPropertySource
    static void ownIndex(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "publish_flow");
    }





    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerFilterService filterService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private String token;

    @BeforeEach
    void setUp() {
        // First statement of the only @BeforeEach: JUnit does not order sibling
        // @BeforeEach methods, so a separate wipe method could run after setup.
        catalogRepository.deleteAll();
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        statsRepository.deleteAll();
        plannerRepository.deleteAll();
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "publish-owner@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    private long entityFilterRows(UUID plannerId) {
        return entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(plannerId))
                .count();
    }

    @Test
    @DisplayName("published-title-edit-consistent: a title edit shows in the public list and detail immediately; a title-only edit leaves the filter index alone")
    void publishedTitleEditConsistent_WhenTitleEdited_ListAndDetailImmediate() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .title("Before Edit")
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
        long filterRowsBefore = entityFilterRows(planner.getId());
        assertThat(filterRowsBefore).isPositive();

        // Owner edits only the title: the client sends the full document with
        // everything but the title unchanged (the wire contract requires it)
        UpsertPlannerRequest titleEdit = new UpsertPlannerRequest(
                planner.getId().toString(), "5F", "After Edit", PlannerStatus.SAVED,
                planner.getContentJson(), 6,
                PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), null);
        mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(titleEdit)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("After Edit"));
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After Edit"));

        assertThat(entityFilterRows(planner.getId()))
                .as("a title-only edit does not rebuild the entity filter")
                .isEqualTo(filterRowsBefore);
    }

    @Test
    @DisplayName("publish-single-request: one content-carrying request creates the draft server-side and publishes it atomically")
    void publishSingleRequest_WhenContentCarried_CreatesAndPublishesAtomically() throws Exception {
        // The draft exists only client-side: nothing on the server yet
        UUID plannerId = UUID.randomUUID();
        String content = TestDataFactory.planner(owner).build().getContentJson();
        // Creating strictly requires the CURRENT game content version
        LegacyPublishRequest publishRequest = new LegacyPublishRequest(
                true, plannerId.toString(), "5F", "One-Shot Publish", PlannerStatus.SAVED,
                content, 7, PlannerType.MIRROR_DUNGEON, null, null);

        mockMvc.perform(put("/api/planner/md/{id}/publish", plannerId)
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.title").value("One-Shot Publish"));

        assertThat(plannerRepository.existsById(plannerId))
                .as("the single request created the planner").isTrue();
        assertThat(catalogRepository.existsById(plannerId))
                .as("the single request published it (catalog row present)").isTrue();
        assertThat(entityFilterRows(plannerId))
                .as("the single request built the filter index").isPositive();

        mockMvc.perform(put("/api/planner/md/{id}/publish", plannerId)
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"published\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
        assertThat(catalogRepository.existsById(plannerId)).isFalse();
    }
}
