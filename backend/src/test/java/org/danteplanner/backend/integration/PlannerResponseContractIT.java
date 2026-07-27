package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.entity.PlannerStatus;

/**
 * Wire-contract pin for the detail and owner-list planner DTOs: the serialized field
 * sets must stay identical across the god-table decomposition (column renames are
 * internal; the JSON contract is frozen).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerResponseContractIT extends SharedMySqlContainerSupport {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    private User owner;
    private String token;
    private Planner published;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "contract-owner@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        published = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }


    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("responseContractStable_WhenOwnerDetail_FieldSetUnchanged")
    void responseContractStable_WhenOwnerDetail_FieldSetUnchanged() throws Exception {
        String json = mockMvc.perform(get("/api/planner/md/{id}", published.getId())
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(objectMapper.readTree(json))).containsExactlyInAnyOrder(
                "id", "title", "category", "status", "content",
                "schemaVersion", "contentVersion", "plannerType", "syncVersion",
                "deviceId", "createdAt", "lastModifiedAt", "savedAt",
                "published", "upvotes");
    }

    @Test
    @DisplayName("responseContractStable_WhenOwnerList_FieldSetUnchanged")
    void responseContractStable_WhenOwnerList_FieldSetUnchanged() throws Exception {
        String json = mockMvc.perform(get("/api/planner/md")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(json).get("content").get(0);
        assertThat(fieldNames(first)).containsExactlyInAnyOrder(
                "id", "title", "category", "plannerType", "status",
                "syncVersion", "lastModifiedAt");
    }

    @Test
    @DisplayName("list-card-fields: the public list card carries firstPublishedAt and drops contentVersion and lastModifiedAt")
    void listCardFields_WhenListed_TrimmedCardShape() throws Exception {
        PlannerStats stats =
                PlannerStats.builder()
                        .plannerId(published.getId())
                        .build();
        statsRepository.save(stats);
        catalogService.add(published);

        String json = mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode card = objectMapper.readTree(json).get("content").get(0);
        assertThat(fieldNames(card)).containsExactlyInAnyOrder(
                "id", "title", "category", "plannerType", "selectedKeywords",
                "authorUsernameEpithet", "authorUsernameSuffix", "upvotes",
                "createdAt", "viewCount", "firstPublishedAt",
                "hasUpvoted", "isBookmarked", "commentCount");
    }

    @Test
    @DisplayName("unpublished-changes-visible-to-owner: the owner detail carries status and published so the FE derives modified-but-not-published")
    void unpublishedChangesVisibleToOwner_WhenStatusDirty_StatusAndPublishedCarried() throws Exception {
        // A published planner whose edit state went dirty (draft) after publish
        published.getContent().setStatus(PlannerStatus.DRAFT);
        plannerRepository.save(published);

        mockMvc.perform(get("/api/planner/md/{id}", published.getId())
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.published").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("draft"));
    }

    @Test
    @DisplayName("responseContractStable_WhenPublishedDetail_FieldSetUnchanged")
    void responseContractStable_WhenPublishedDetail_FieldSetUnchanged() throws Exception {
        String json = mockMvc.perform(get("/api/planner/md/published/{id}", published.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(objectMapper.readTree(json))).containsExactlyInAnyOrder(
                "id", "title", "category", "plannerType", "selectedKeywords",
                "authorUsernameEpithet", "authorUsernameSuffix", "upvotes", "viewCount",
                "createdAt", "lastModifiedAt", "hasUpvoted", "isBookmarked",
                "content", "schemaVersion", "contentVersion", "status", "syncVersion",
                "isSubscribed", "hasReported", "commentCount", "ownerNotificationsEnabled");
    }
}
