package org.danteplanner.backend.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.UpsertRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.AuthCookies.performAuthed;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A stale write is refused only when it would actually move the row. The version the client wrote
 * against having advanced is no longer sufficient on its own: if every field the request carries
 * already holds the stored value — content compared as a parsed tree, so the storage layer's
 * rendering of a document counts as that document — the write is acknowledged with the stored
 * version and nothing is written.
 *
 * <p>The version numbers in the scenario names are illustrative; what these assert is staleness by
 * one, so each case drives the planner to a known version and presents the one before it.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStaleWriteArbitrationIT extends SharedMySqlContainerSupport {

    private static final String BASE_TITLE = "Arbitration Fixture";
    private static final int CONTENT_VERSION = 8;

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

    private User owner;
    private String token;
    private UUID device;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "stale-arbitration@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        device = UUID.randomUUID();
    }

    /** Which document a request carries. */
    private enum ContentChoice { AS_CREATED, PULLED_RENDERING, DIFFERENT }

    /**
     * The persisted fields a client write can move. Content is held parsed: MySQL re-serializes a
     * JSON column, so the stored string differs from the written one even when the document does
     * not, and a byte comparison would report every save as a change.
     */
    private record RowState(
            String title,
            PlannerStatus status,
            String category,
            JsonNode content,
            Set<String> keywords,
            int contentVersion,
            int schemaVersion,
            UUID deviceId) {
    }

    private UpsertRequests.Builder write(UUID id) {
        return UpsertRequests.draft()
                .id(id.toString())
                .title(BASE_TITLE)
                .content(TestDataFactory.VALID_CONTENT)
                .status(PlannerStatus.DRAFT)
                .contentVersion(CONTENT_VERSION);
    }

    private ResultActions send(UUID id, UpsertPlannerRequest request, boolean force) throws Exception {
        MockHttpServletRequestBuilder builder = put("/api/planner/md/{id}", id).with(withCsrf())
                .cookie(AuthCookies.deviceId(device))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        if (force) {
            builder = builder.param("force", "true");
        }
        return performAuthed(mockMvc, builder, token);
    }

    private JsonNode readBack(UUID id) throws Exception {
        String json = performAuthed(mockMvc, get("/api/planner/md/{id}", id), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private PlannerContent storedRow(UUID id) {
        return plannerRepository.findAggregateForOwner(id, owner.getId()).orElseThrow().getContent();
    }

    private RowState stateOf(UUID id) throws Exception {
        PlannerContent row = storedRow(id);
        return new RowState(
                row.getTitle(), row.getStatus(), row.getCategory(),
                objectMapper.readTree(row.getContent()), row.getSelectedKeywords(),
                row.getGameContentVersion(), row.getContentSchemaVersion(), row.getDeviceId());
    }

    /** A planner created and then saved once, so there is a version to be stale against. */
    private UUID plannerPastItsFirstSave() throws Exception {
        UUID id = UUID.randomUUID();
        send(id, write(id).build(), false).andExpect(status().isCreated());
        send(id, write(id).syncVersion(1L).build(), false).andExpect(status().isOk());
        return id;
    }

    /** The same document as {@link TestDataFactory#VALID_CONTENT} with one selected buff removed. */
    private String divergentContent() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(TestDataFactory.VALID_CONTENT);
        root.set("selectedBuffIds", objectMapper.createArrayNode().add(100));
        return objectMapper.writeValueAsString(root);
    }

    private String contentFor(ContentChoice choice, UUID id) throws Exception {
        return switch (choice) {
            case AS_CREATED -> TestDataFactory.VALID_CONTENT;
            case PULLED_RENDERING -> readBack(id).get("content").asString();
            case DIFFERENT -> divergentContent();
        };
    }

    // stale-identical-save-acks-current-version
    @Test
    void staleIdenticalSave_WhenEveryCarriedFieldMatches_AcksCurrentVersion() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();
        Instant modifiedBefore = storedRow(id).getLastModifiedAt();
        String bytesBefore = storedRow(id).getContent();
        long lockBefore = storedRow(id).getRowLockVersion();

        send(id, write(id).syncVersion(stored - 1).build(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(stored));

        PlannerContent after = storedRow(id);
        assertThat(after.getSyncVersion()).isEqualTo(stored);
        assertThat(after.getContent()).isEqualTo(bytesBefore);
        assertThat(after.getLastModifiedAt()).isEqualTo(modifiedBefore);
        assertThat(after.getRowLockVersion())
                .as("the arbitration branch performs no write, so the row lock version cannot move")
                .isEqualTo(lockBefore);
    }

    // retry-after-committed-save-is-idempotent
    @Test
    void retryAfterCommittedSave_WhenResentAtTheConsumedVersion_IsIdempotent() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long consumed = storedRow(id).getSyncVersion();

        UpsertPlannerRequest committing = write(id).title("Retried Title").syncVersion(consumed).build();
        send(id, committing, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(consumed + 1));

        Instant modifiedBefore = storedRow(id).getLastModifiedAt();
        long lockBefore = storedRow(id).getRowLockVersion();

        send(id, committing, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(consumed + 1));

        PlannerContent after = storedRow(id);
        assertThat(after.getSyncVersion()).isEqualTo(consumed + 1);
        assertThat(after.getLastModifiedAt()).isEqualTo(modifiedBefore);
        assertThat(after.getRowLockVersion()).isEqualTo(lockBefore);
    }

    // pulled-rendering-counts-as-identical
    @Test
    void pulledRendering_WhenResavedAtAStaleVersion_CountsAsIdentical() throws Exception {
        UUID id = UUID.randomUUID();
        send(id, write(id).build(), false).andExpect(status().isCreated());

        send(id, write(id).build(), true).andExpect(status().isOk());

        long stored = storedRow(id).getSyncVersion();
        String pulled = readBack(id).get("content").asString();

        assertThat(pulled)
                .as("the case only tests anything while the stored rendering differs from the sent bytes")
                .isNotEqualTo(TestDataFactory.VALID_CONTENT);

        send(id, write(id)
                        .content(pulled)
                        .syncVersion(stored - 1)
                        .build(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(stored));
    }

    // stale-title-change-still-conflicts
    @Test
    void staleTitleChange_WhenContentIsTreeEqual_StillConflicts() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, write(id)
                        .title("A Different Title")
                        .syncVersion(stored - 1)
                        .build(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                .andExpect(jsonPath("$.serverVersion").value(stored));
    }

    // stale-different-content-conflicts
    @Test
    void staleDifferentContent_WhenNotTreeEqual_Conflicts() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, write(id)
                        .content(divergentContent())
                        .syncVersion(stored - 1)
                        .build(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                .andExpect(jsonPath("$.serverVersion").value(stored));
    }

    // unparseable-content-falls-through-to-conflict
    @Test
    void unparseableContent_WhenCarriedByAStaleWrite_FallsThroughToConflict() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, write(id)
                        .content("{\"equipment\":")
                        .syncVersion(stored - 1)
                        .build(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertThat(storedRow(id).getSyncVersion()).isEqualTo(stored);
    }

    // no-version-still-conflicts
    @Test
    void noVersion_WhenPlannerExistsAndNotForced_Conflicts() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, write(id).build(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertThat(storedRow(id).getSyncVersion()).isEqualTo(stored);
    }

    // force-still-bypasses
    @Test
    void force_WhenCarryingIdenticalStateAtAStaleVersion_StillWrites() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, write(id).syncVersion(stored - 1).build(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(stored + 1));

        assertThat(storedRow(id).getSyncVersion()).isEqualTo(stored + 1);
    }

    static Stream<Arguments> writeVariants() {
        return Stream.of(
                Arguments.of("identical", BASE_TITLE, ContentChoice.AS_CREATED, PlannerStatus.DRAFT, CONTENT_VERSION),
                Arguments.of("pulled rendering", BASE_TITLE, ContentChoice.PULLED_RENDERING, PlannerStatus.DRAFT,
                        CONTENT_VERSION),
                Arguments.of("moved title", "Moved Title", ContentChoice.AS_CREATED, PlannerStatus.DRAFT,
                        CONTENT_VERSION),
                Arguments.of("moved content", BASE_TITLE, ContentChoice.DIFFERENT, PlannerStatus.DRAFT,
                        CONTENT_VERSION),
                Arguments.of("moved status", BASE_TITLE, ContentChoice.AS_CREATED, PlannerStatus.SAVED,
                        CONTENT_VERSION),
                Arguments.of("moved content version", BASE_TITLE, ContentChoice.AS_CREATED, PlannerStatus.DRAFT,
                        CONTENT_VERSION - 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writeVariants")
    void arbitration_WhenComparedWithApplyingTheSameWrite_IsBiconditional(
            String label, String title, ContentChoice choice, PlannerStatus status, int contentVersion)
            throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();
        String content = contentFor(choice, id);
        RowState before = stateOf(id);

        int staleStatus = send(id, write(id)
                        .title(title)
                        .content(content)
                        .status(status)
                        .contentVersion(contentVersion)
                        .syncVersion(stored - 1)
                        .build(), false)
                .andReturn().getResponse().getStatus();

        assertThat(staleStatus).isIn(200, 409);
        assertThat(stateOf(id))
                .as("%s: neither an acknowledgement nor a conflict may write", label)
                .isEqualTo(before);

        send(id, write(id)
                        .title(title)
                        .content(content)
                        .status(status)
                        .contentVersion(contentVersion)
                        .syncVersion(stored)
                        .build(), false)
                .andExpect(status().isOk());

        boolean movedAField = !stateOf(id).equals(before);
        assertThat(staleStatus == 200)
                .as("%s: acknowledged=%s but applying the write moved a field=%s",
                        label, staleStatus == 200, movedAField)
                .isEqualTo(!movedAField);
    }
}
