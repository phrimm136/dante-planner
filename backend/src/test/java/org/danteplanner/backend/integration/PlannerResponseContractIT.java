package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.AuthCookies.performAuthed;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.dto.PlannerBatchRequest;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.support.AuthCookies;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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

    @Autowired
    private PlannerVoteRepository voteRepository;

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

    /**
     * The list page carries every published planner in the shared database, so the card under
     * assertion is selected by id rather than by position.
     */
    private JsonNode cardFor(String json, UUID plannerId) throws Exception {
        JsonNode card = null;
        for (JsonNode candidate : objectMapper.readTree(json).get("content")) {
            if (plannerId.toString().equals(candidate.get("id").asText())) {
                card = candidate;
            }
        }
        assertThat(card).as("the published list carries planner %s", plannerId).isNotNull();
        return card;
    }

    /**
     * Reads a viewer-context flag, failing unless it is present and serialized as a JSON boolean
     * rather than omitted or null.
     */
    private boolean flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        assertThat(value).as("%s is present on the wire", field).isNotNull();
        assertThat(value.isBoolean()).as("%s is a JSON boolean", field).isTrue();
        return value.booleanValue();
    }

    private void engageAsOwner() {
        voteRepository.insert(new PlannerVote(owner.getId(), published.getId(), VoteType.UP));
    }

    @Test
    void responseContractStable_WhenOwnerDetail_FieldSetUnchanged() throws Exception {
        published.getContent().setDeviceId(UUID.randomUUID());
        plannerRepository.save(published);

        String json = performAuthed(mockMvc, get("/api/planner/md/{id}", published.getId()), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(objectMapper.readTree(json))).containsExactlyInAnyOrder(
                "id", "title", "category", "status", "content",
                "schemaVersion", "contentVersion", "plannerType", "syncVersion",
                "deviceId", "createdAt", "lastModifiedAt", "savedAt",
                "published");
    }

    @Test
    void responseContractStable_WhenOwnerDetailHasNoDevice_OmitsDeviceId() throws Exception {
        String json = performAuthed(mockMvc, get("/api/planner/md/{id}", published.getId()), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(objectMapper.readTree(json))).doesNotContain("deviceId");
    }

    @Test
    void responseContractStable_WhenOwnerList_FieldSetUnchanged() throws Exception {
        String json = performAuthed(mockMvc, get("/api/planner/md"), token)
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

        JsonNode card = cardFor(json, published.getId());
        assertThat(fieldNames(card)).containsExactlyInAnyOrder(
                "id", "title", "category", "plannerType", "selectedKeywords",
                "authorUsernameEpithet", "authorUsernameSuffix", "upvotes",
                "createdAt", "viewCount", "firstPublishedAt", "commentCount",
                "hasUpvoted", "isBookmarked");
        assertThat(flag(card, "hasUpvoted"))
                .as("an anonymous viewer has no account to have upvoted with")
                .isFalse();
        assertThat(flag(card, "isBookmarked")).isFalse();
    }

    @Test
    @DisplayName("list-card-fields: an authenticated list card carries the viewer's own vote state")
    void listCardFields_WhenListedAsViewer_CarriesUserContext() throws Exception {
        statsRepository.save(PlannerStats.builder().plannerId(published.getId()).build());
        catalogService.add(published);
        engageAsOwner();

        String json = performAuthed(mockMvc, get("/api/planner/md/published"), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode card = cardFor(json, published.getId());
        assertThat(fieldNames(card)).contains("hasUpvoted", "isBookmarked");
        assertThat(flag(card, "hasUpvoted"))
                .as("the viewer's vote row is reflected on the card")
                .isTrue();
    }

    @Test
    @DisplayName("unpublished-changes-visible-to-owner: the owner detail carries status and published so the FE derives modified-but-not-published")
    void unpublishedChangesVisibleToOwner_WhenStatusDirty_StatusAndPublishedCarried() throws Exception {
        // A published planner whose edit state went dirty (draft) after publish
        published.getContent().setStatus(PlannerStatus.DRAFT);
        plannerRepository.save(published);

        performAuthed(mockMvc, get("/api/planner/md/{id}", published.getId()), token)
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.published").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("draft"));
    }

    @Test
    void responseContractStable_WhenPublishedDetail_FieldSetUnchanged() throws Exception {
        String json = mockMvc.perform(get("/api/planner/md/published/{id}", published.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode detail = objectMapper.readTree(json);
        assertThat(fieldNames(detail)).containsExactlyInAnyOrder(
                "id", "title", "category", "plannerType", "selectedKeywords",
                "authorUsernameEpithet", "authorUsernameSuffix", "upvotes", "viewCount",
                "createdAt", "firstPublishedAt", "lastModifiedAt",
                "content", "schemaVersion", "contentVersion", "status", "syncVersion",
                "commentCount", "ownerNotificationsEnabled",
                "hasUpvoted", "isBookmarked", "isSubscribed", "hasReported");
        assertThat(flag(detail, "hasUpvoted")).isFalse();
        assertThat(flag(detail, "isBookmarked")).isFalse();
        assertThat(flag(detail, "isSubscribed")).isFalse();
        assertThat(flag(detail, "hasReported"))
                .as("an anonymous viewer has no account for any viewer-context flag to be true of")
                .isFalse();
    }

    @Test
    void responseContractStable_WhenPublishedDetailAsViewer_CarriesUserContext() throws Exception {
        engageAsOwner();

        String json = performAuthed(
                mockMvc, get("/api/planner/md/published/{id}", published.getId()), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode detail = objectMapper.readTree(json);
        assertThat(fieldNames(detail))
                .contains("hasUpvoted", "isBookmarked", "isSubscribed", "hasReported");
        assertThat(flag(detail, "hasUpvoted"))
                .as("the viewer's vote row is reflected on the detail")
                .isTrue();
        assertThat(flag(detail, "isSubscribed"))
                .as("the viewer holds no subscription row")
                .isFalse();
        assertThat(flag(detail, "hasReported"))
                .as("the viewer holds no report row")
                .isFalse();
    }

    private ResultActions upsert(UUID id, Long syncVersion) throws Exception {
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                id.toString(), "5F", "Contract Upsert", PlannerStatus.DRAFT, TestDataFactory.VALID_CONTENT,
                7, PlannerType.MIRROR_DUNGEON, syncVersion, null);
        return performAuthed(mockMvc, put("/api/planner/md/{id}", id).with(withCsrf())
                        .cookie(AuthCookies.deviceId(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                token);
    }

    @Test
    @DisplayName("no-upvotes-on-planner-response: the owner detail carries no upvotes key")
    void noUpvotes_WhenOwnerDetail_KeyAbsent() throws Exception {
        performAuthed(mockMvc, get("/api/planner/md/{id}", published.getId()), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(published.getId().toString()))
                .andExpect(jsonPath("$.upvotes").doesNotExist());
    }

    @Test
    @DisplayName("no-upvotes-on-planner-response: the upsert response carries no upvotes key on create or update")
    void noUpvotes_WhenUpserted_KeyAbsent() throws Exception {
        UUID id = UUID.randomUUID();

        upsert(id, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.upvotes").doesNotExist());

        upsert(id, 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.upvotes").doesNotExist());
    }

    @Test
    @DisplayName("no-upvotes-on-planner-response: the batch pull elements carry no upvotes key")
    void noUpvotes_WhenBatchPulled_KeyAbsent() throws Exception {
        performAuthed(mockMvc, post("/api/planner/md/batch").with(withCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlannerBatchRequest(List.of(published.getId())))),
                token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(published.getId().toString()))
                .andExpect(jsonPath("$[0].upvotes").doesNotExist());
    }

    @Test
    @DisplayName("no-upvotes-on-planner-response: the GET batch pull elements carry no upvotes key")
    void noUpvotes_WhenBatchPulledByGet_KeyAbsent() throws Exception {
        Planner draft = TestDataFactory.createTestPlanner(plannerRepository, owner, false);

        performAuthed(mockMvc, get("/api/planner/md/batch")
                        .param("ids", published.getId() + "," + draft.getId()),
                token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].upvotes").doesNotExist())
                .andExpect(jsonPath("$[1].id").exists())
                .andExpect(jsonPath("$[1].upvotes").doesNotExist());
    }
}
