package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publish flow over the projections: an owner's title edit on a published
 * planner is visible in the list and detail from the same request on, and
 * publishing is one content-carrying request that upserts and sets published
 * atomically. Unpublish carries no body.
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

    @Test
    @DisplayName("publish-intent-single-request: the intent route's body creates the draft server-side and publishes it")
    void publishIntentSingleRequest_WhenContentCarried_CreatesAndPublishesAtomically() throws Exception {
        UUID plannerId = UUID.randomUUID();
        String content = TestDataFactory.planner(owner).build().getContentJson();
        UpsertPlannerRequest publishRequest = new UpsertPlannerRequest(
                plannerId.toString(), "5F", "Intent One-Shot", PlannerStatus.SAVED,
                content, 7, PlannerType.MIRROR_DUNGEON, null, null);

        mockMvc.perform(post("/api/planner/md/{id}/publish", plannerId)
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.title").value("Intent One-Shot"));

        assertThat(plannerRepository.existsById(plannerId))
                .as("the single request created the planner").isTrue();
        assertThat(catalogRepository.existsById(plannerId))
                .as("the single request published it (catalog row present)").isTrue();
        assertThat(entityFilterRows(plannerId))
                .as("the single request built the filter index").isPositive();
    }

    @Test
    @DisplayName("publish-intent-rejects-partial-content: an incomplete body is refused before anything is stored")
    void publishIntentSingleRequest_WhenContentIncomplete_Returns400AndStoresNothing() throws Exception {
        UUID plannerId = UUID.randomUUID();

        mockMvc.perform(post("/api/planner/md/{id}/publish", plannerId)
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"id\":\"" + plannerId + "\",\"title\":\"No Category\"}"))
                .andExpect(status().isBadRequest());

        assertThat(plannerRepository.existsById(plannerId))
                .as("a refused body creates nothing").isFalse();
    }

    private static final int CURRENT_CONTENT_VERSION = 7;

    private UpsertPlannerRequest upsertBody(UUID id, String title, String content, Long syncVersion) {
        return new UpsertPlannerRequest(
                id.toString(), "5F", title, PlannerStatus.SAVED, content,
                CURRENT_CONTENT_VERSION, PlannerType.MIRROR_DUNGEON, syncVersion, null);
    }

    private ResultActions sendAs(UUID device, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(request
                .cookie(AuthCookies.session(token, device))
                .with(withCsrf())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String divergentContent() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(TestDataFactory.VALID_CONTENT);
        root.set("selectedBuffIds", objectMapper.createArrayNode().add(100));
        return objectMapper.writeValueAsString(root);
    }

    /** A planner saved once from {@code device}, holding content that differs from the publish body's. */
    private long savedPlanner(UUID id, UUID device) throws Exception {
        sendAs(device, put("/api/planner/md/{id}", id), upsertBody(id, "Saved Draft", divergentContent(), null))
                .andExpect(status().isCreated());
        return plannerRepository.findAggregateForOwner(id, owner.getId()).orElseThrow().getSyncVersion();
    }

    private Planner stored(UUID id) {
        return plannerRepository.findAggregateForOwner(id, owner.getId()).orElseThrow();
    }

    @Test
    @DisplayName("publish-carries-the-save: one content-carrying publish leaves the state save-then-publish leaves")
    void publishWithContent_WhenLocalEditsUnsaved_EqualsSaveThenPublish() throws Exception {
        UUID device = UUID.randomUUID();
        UUID throughTwoRequests = UUID.randomUUID();
        UUID throughOneRequest = UUID.randomUUID();
        long twoVersion = savedPlanner(throughTwoRequests, device);
        long oneVersion = savedPlanner(throughOneRequest, device);

        sendAs(device, put("/api/planner/md/{id}", throughTwoRequests),
                upsertBody(throughTwoRequests, "Edited Title", TestDataFactory.VALID_CONTENT, twoVersion))
                .andExpect(status().isOk());
        String twoBody = mockMvc.perform(post("/api/planner/md/{id}/publish", throughTwoRequests)
                        .cookie(AuthCookies.session(token, device))
                        .with(withCsrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String oneBody = sendAs(device, post("/api/planner/md/{id}/publish", throughOneRequest),
                        upsertBody(throughOneRequest, "Edited Title", TestDataFactory.VALID_CONTENT, oneVersion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ObjectNode viaTwo = (ObjectNode) objectMapper.readTree(twoBody);
        ObjectNode viaOne = (ObjectNode) objectMapper.readTree(oneBody);
        assertThat(viaOne.get("published").asBoolean()).isTrue();
        assertThat(viaOne.get("title").asText()).isEqualTo("Edited Title");
        assertThat(viaOne.get("syncVersion").asLong()).isEqualTo(oneVersion + 1);
        assertThat(objectMapper.readTree(viaOne.get("content").asText()))
                .isEqualTo(objectMapper.readTree(TestDataFactory.VALID_CONTENT));

        List<String> perRowFields = List.of("id", "createdAt", "lastModifiedAt", "savedAt");
        viaTwo.remove(perRowFields);
        viaOne.remove(perRowFields);
        viaTwo.set("content", objectMapper.readTree(viaTwo.get("content").asText()));
        viaOne.set("content", objectMapper.readTree(viaOne.get("content").asText()));
        assertThat(viaOne).isEqualTo(viaTwo);
    }

    @Test
    @DisplayName("stale-publish-conflicts-like-a-stale-save: the same stale body answers the same conflict on both routes")
    void publishWithContent_WhenSyncVersionStale_AnswersTheSaveConflict() throws Exception {
        UUID device = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        long version = savedPlanner(id, device);
        sendAs(device, put("/api/planner/md/{id}", id),
                upsertBody(id, "Advanced", divergentContent(), version))
                .andExpect(status().isOk());
        UpsertPlannerRequest stale = upsertBody(id, "Stale Edit", TestDataFactory.VALID_CONTENT, version);

        var saveResponse = sendAs(device, put("/api/planner/md/{id}", id), stale)
                .andReturn().getResponse();
        var publishResponse = sendAs(device, post("/api/planner/md/{id}/publish", id), stale)
                .andReturn().getResponse();

        assertThat(saveResponse.getStatus()).isEqualTo(409);
        assertThat(publishResponse.getStatus()).isEqualTo(saveResponse.getStatus());
        JsonNode saveProblem = objectMapper.readTree(saveResponse.getContentAsString());
        JsonNode publishProblem = objectMapper.readTree(publishResponse.getContentAsString());
        assertThat(publishProblem.get("code")).isEqualTo(saveProblem.get("code"));
        assertThat(publishProblem.get("serverVersion")).isEqualTo(saveProblem.get("serverVersion"));
        assertThat(stored(id).isPublished()).as("a refused publish publishes nothing").isFalse();
        assertThat(stored(id).getTitle()).isEqualTo("Advanced");
    }

    @Test
    @DisplayName("publish-stamps-the-device: the publishing device is stored, so another device's stale resend conflicts")
    void publishWithContent_WhenStaleResendFromAnotherDevice_ConflictsInsteadOfAcking() throws Exception {
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        long version = savedPlanner(id, firstDevice);
        UpsertPlannerRequest published = upsertBody(id, "Published", TestDataFactory.VALID_CONTENT, version);

        sendAs(secondDevice, post("/api/planner/md/{id}/publish", id), published)
                .andExpect(status().isOk());
        assertThat(stored(id).getDeviceId()).isEqualTo(secondDevice);

        sendAs(firstDevice, post("/api/planner/md/{id}/publish", id), published)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));
        assertThat(stored(id).getDeviceId()).isEqualTo(secondDevice);
    }

    @Test
    @DisplayName("unpublish-ignores-a-body: the unpublish intent saves no content even when a body is sent")
    void unpublishIntent_WhenBodySent_SavesNoContent() throws Exception {
        UUID device = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        long version = savedPlanner(id, device);
        sendAs(device, post("/api/planner/md/{id}/publish", id),
                upsertBody(id, "Published", TestDataFactory.VALID_CONTENT, version))
                .andExpect(status().isOk());
        long publishedVersion = stored(id).getSyncVersion();

        sendAs(device, post("/api/planner/md/{id}/unpublish", id),
                upsertBody(id, "Smuggled Title", divergentContent(), publishedVersion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        Planner after = stored(id);
        assertThat(after.getTitle()).isEqualTo("Published");
        assertThat(after.getSyncVersion()).isEqualTo(publishedVersion);
    }

    @Test
    @DisplayName("legacy-unpublish-with-content-stamps-the-device: the delegate's content path stores the device")
    void legacyUnpublish_WhenContentCarried_StoresTheDevice() throws Exception {
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        long version = savedPlanner(id, firstDevice);
        LegacyPublishRequest unpublish = new LegacyPublishRequest(
                false, id.toString(), "5F", "Withdrawn", PlannerStatus.SAVED,
                TestDataFactory.VALID_CONTENT, CURRENT_CONTENT_VERSION, PlannerType.MIRROR_DUNGEON, version, null);

        sendAs(secondDevice, put("/api/planner/md/{id}/publish", id), unpublish)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.title").value("Withdrawn"));

        assertThat(stored(id).getDeviceId()).isEqualTo(secondDevice);
    }

    private JsonNode ownerCopy(UUID plannerId) throws Exception {
        String body = mockMvc.perform(get("/api/planner/md/{id}", plannerId)
                        .cookie(AuthCookies.accessToken(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void saveBack(JsonNode received, String title, String content) throws Exception {
        UpsertPlannerRequest save = new UpsertPlannerRequest(
                received.get("id").asText(), received.get("category").asText(), title,
                objectMapper.treeToValue(received.get("status"), PlannerStatus.class), content,
                received.get("contentVersion").asInt(), objectMapper.treeToValue(received.get("plannerType"), PlannerType.class),
                received.get("syncVersion").asLong(), null);
        mockMvc.perform(put("/api/planner/md/{id}", received.get("id").asText())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(save)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("title-only-save-skips-rebuild: resending the content exactly as the owner received it runs no filter rebuild")
    void publishedTitleSave_WhenContentResentAsReceived_SkipsFilterRebuild() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        JsonNode received = ownerCopy(planner.getId());
        assertThat(entityFilterRows(planner.getId())).isZero();

        saveBack(received, "Renamed", received.get("content").asText());

        assertThat(entityFilterRows(planner.getId()))
                .as("no rebuild ran, so the deliberately unbuilt index stays empty")
                .isZero();
    }

    @Test
    @DisplayName("composition-change-rebuilds: a save that swaps an identity rebuilds the filter index")
    void publishedContentSave_WhenIdentityChanged_RebuildsFilters() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        JsonNode received = ownerCopy(planner.getId());
        assertThat(entityFilterRows(planner.getId())).isZero();

        ObjectNode edited = (ObjectNode) objectMapper.readTree(received.get("content").asText());
        ((ObjectNode) edited.get("equipment").get("01").get("identity")).put("id", "10102");
        saveBack(received, received.get("title").asText(), objectMapper.writeValueAsString(edited));

        assertThat(entityFilterRows(planner.getId()))
                .as("the changed composition rebuilt the index")
                .isPositive();
    }
}
