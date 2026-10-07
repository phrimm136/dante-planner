package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.CatalogEffects;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
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
class PlannerPublishFlowIT extends ExclusiveDatabaseSupport {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerCatalogSyncService catalogSyncService;

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
        emptyPlannerTables(jdbcTemplate);
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

    private static final int UNREACHABLE_ENTITY_ID = 424242;

    private long entityFilterRows(UUID plannerId) {
        return entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(plannerId))
                .count();
    }

    // published-title-edit-consistent
    @Test
    void publishedTitleEditConsistent_WhenTitleEdited_ListAndDetailImmediateAndFilterIndexUntouched() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .title("Before Edit")
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        catalogSyncService.syncPlanner(planner.getId(), List.of());
        PlannerEntityFilter planted = entityFilterRepository.save(
                new PlannerEntityFilter(ContentEntityType.THEME_PACK, UNREACHABLE_ENTITY_ID, planner.getId()));
        long filterRowsBefore = entityFilterRows(planner.getId());
        assertThat(filterRowsBefore).isGreaterThan(1);

        // Owner edits only the title: the client sends the full document with
        // everything but the title unchanged (the wire contract requires it)
        UpsertPlannerRequest titleEdit = UpsertRequests.resending(planner)
                .title("After Edit")
                .status(PlannerStatus.SAVED)
                .build();
        mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(titleEdit)))
                .andExpect(status().isOk());
        CatalogEffects.drain(catalogSyncService, jdbcTemplate, planner.getId());

        mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("After Edit"));
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After Edit"));

        assertThat(entityFilterRepository.findById(planted.getId()))
                .as("a title-only edit does not rebuild the entity filter: a rebuild clears every row "
                        + "for the planner, so the planted row would be gone")
                .isPresent();
        assertThat(entityFilterRows(planner.getId())).isEqualTo(filterRowsBefore);
    }

    @Test
    void publishIntentSingleRequest_WhenContentCarried_CreatesAndPublishesAtomically() throws Exception {
        UUID plannerId = UUID.randomUUID();
        String content = TestDataFactory.planner(owner).build().getContentJson();
        UpsertPlannerRequest publishRequest = UpsertRequests.draft()
                .id(plannerId.toString())
                .title("Intent One-Shot")
                .status(PlannerStatus.SAVED)
                .content(content)
                .build();

        mockMvc.perform(post("/api/planner/md/{id}/publish", plannerId)
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.title").value("Intent One-Shot"));
        CatalogEffects.drain(catalogSyncService, jdbcTemplate, plannerId);

        assertThat(plannerRepository.existsById(plannerId))
                .as("the single request created the planner").isTrue();
        assertThat(catalogRepository.existsById(plannerId))
                .as("the single request published it (catalog row present)").isTrue();
        assertThat(entityFilterRows(plannerId))
                .as("the single request built the filter index").isPositive();
    }

    @Test
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

    private UpsertPlannerRequest upsertBody(UUID id, String title, String content, Long syncVersion) {
        return UpsertRequests.draft()
                .id(id.toString())
                .title(title)
                .status(PlannerStatus.SAVED)
                .content(content)
                .syncVersion(syncVersion)
                .build();
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

    private JsonNode ownerCopy(UUID plannerId) throws Exception {
        String body = mockMvc.perform(get("/api/planner/md/{id}", plannerId)
                        .cookie(AuthCookies.accessToken(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void saveBack(JsonNode received, String title, String content) throws Exception {
        UpsertPlannerRequest save = UpsertRequests.draft()
                .id(received.get("id").asText())
                .category(received.get("category").asText())
                .title(title)
                .status(objectMapper.treeToValue(received.get("status"), PlannerStatus.class))
                .content(content)
                .contentVersion(received.get("contentVersion").asInt())
                .plannerType(objectMapper.treeToValue(received.get("plannerType"), PlannerType.class))
                .syncVersion(received.get("syncVersion").asLong())
                .selectedKeywords(null)
                .build();
        mockMvc.perform(put("/api/planner/md/{id}", received.get("id").asText())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(save)))
                .andExpect(status().isOk());
    }

    @Test
    void publishedTitleSave_WhenContentResentAsReceived_SkipsFilterRebuild() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        JsonNode received = ownerCopy(planner.getId());
        assertThat(entityFilterRows(planner.getId())).isZero();

        saveBack(received, "Renamed", received.get("content").asText());
        CatalogEffects.drain(catalogSyncService, jdbcTemplate, planner.getId());

        assertThat(entityFilterRows(planner.getId()))
                .as("no rebuild ran, so the deliberately unbuilt index stays empty")
                .isZero();
    }

    @Test
    void publishedContentSave_WhenIdentityChanged_RebuildsFilters() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        JsonNode received = ownerCopy(planner.getId());
        assertThat(entityFilterRows(planner.getId())).isZero();

        ObjectNode edited = (ObjectNode) objectMapper.readTree(received.get("content").asText());
        ((ObjectNode) edited.get("equipment").get("1").get("identity")).put("id", "10102");
        saveBack(received, received.get("title").asText(), objectMapper.writeValueAsString(edited));
        CatalogEffects.drain(catalogSyncService, jdbcTemplate, planner.getId());

        assertThat(entityFilterRows(planner.getId()))
                .as("the changed composition rebuilt the index")
                .isPositive();
    }

    @Test
    void publish_WhenStoredContentSelectsAnUnknownKeyword_Returns400UnknownKeywordAndStaysUnpublished() throws Exception {
        Planner known = TestDataFactory.planner(owner).title("Known Keyword Draft")
                .selectedKeywords(Set.of("Sinking")).save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(known.getId()).build());
        Planner unknown = TestDataFactory.planner(owner).title("Unknown Keyword Draft")
                .selectedKeywords(Set.of("Sinking", "NotAKeyword")).save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(unknown.getId()).build());

        mockMvc.perform(post("/api/planner/md/{id}/publish", known.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
        mockMvc.perform(post("/api/planner/md/{id}/publish", unknown.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_KEYWORD"));

        assertThat(stored(unknown.getId()).isPublished()).isFalse();
        assertThat(catalogRepository.existsById(unknown.getId())).isFalse();
    }

    private Planner publishedWithUnknownKeyword(String title) {
        Planner planner = TestDataFactory.planner(owner).title(title)
                .selectedKeywords(Set.of("Sinking", "NotAKeyword")).published(true).save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        catalogSyncService.syncPlanner(planner.getId(), List.of());
        return stored(planner.getId());
    }

    @Test
    void publishedTitleSave_WhenStoredContentSelectsAnUnknownKeyword_Returns400UnknownKeyword() throws Exception {
        Planner planner = publishedWithUnknownKeyword("Published Unknown Keyword");
        UpsertPlannerRequest titleEdit = UpsertRequests.resending(planner)
                .title("Retitled")
                .status(PlannerStatus.SAVED)
                .build();

        mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(titleEdit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_KEYWORD"))
                .andExpect(jsonPath("$.detail").value("Unknown keywords: [NotAKeyword]"));

        assertThat(stored(planner.getId()).getTitle()).isEqualTo("Published Unknown Keyword");
    }

    @Test
    void publishWithContent_WhenTheDraftSelectsAnUnknownKeyword_Returns400AndRollsBackTheSave() throws Exception {
        Planner draft = TestDataFactory.planner(owner).title("Draft Before Publish")
                .selectedKeywords(Set.of("Sinking")).save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(draft.getId()).build());
        UpsertPlannerRequest publishBody = UpsertRequests.resending(draft)
                .title("Draft Published With Unknown")
                .status(PlannerStatus.SAVED)
                .content(TestDataFactory.withSelectedKeywords(draft.getContentJson(), List.of("NotAKeyword")))
                .build();

        mockMvc.perform(post("/api/planner/md/{id}/publish", draft.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_KEYWORD"));

        Planner after = stored(draft.getId());
        assertThat(after.isPublished()).isFalse();
        assertThat(after.getTitle()).isEqualTo("Draft Before Publish");
    }
}
