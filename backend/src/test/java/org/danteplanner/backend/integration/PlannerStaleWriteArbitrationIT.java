package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStaleWriteArbitrationIT extends SharedMySqlContainerSupport {

    private static final String BASE_TITLE = "Arbitration Fixture";
    private static final String CATEGORY = "5F";
    private static final int CONTENT_VERSION = 7;

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

    private UpsertPlannerRequest request(
            UUID id, String title, String content, PlannerStatus status, int contentVersion, Long syncVersion) {
        return new UpsertPlannerRequest(
                id.toString(), CATEGORY, title, status, content,
                contentVersion, PlannerType.MIRROR_DUNGEON, syncVersion, null);
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
        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT, CONTENT_VERSION, null),
                false).andExpect(status().isCreated());
        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT, CONTENT_VERSION, 1L),
                false).andExpect(status().isOk());
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
            case PULLED_RENDERING -> readBack(id).get("content").asText();
            case DIFFERENT -> divergentContent();
        };
    }

    @Test
    @DisplayName("stale-identical-save-acks-current-version: acknowledged with the stored version, nothing written")
    void staleIdenticalSave_WhenEveryCarriedFieldMatches_AcksCurrentVersion() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();
        Instant modifiedBefore = storedRow(id).getLastModifiedAt();
        String bytesBefore = storedRow(id).getContent();
        long lockBefore = storedRow(id).getRowLockVersion();

        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT,
                        CONTENT_VERSION, stored - 1), false)
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

    @Test
    @DisplayName("retry-after-committed-save-is-idempotent: the byte-identical retry is acknowledged")
    void retryAfterCommittedSave_WhenResentAtTheConsumedVersion_IsIdempotent() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long consumed = storedRow(id).getSyncVersion();

        UpsertPlannerRequest committing = request(
                id, "Retried Title", TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT, CONTENT_VERSION, consumed);
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

    @Test
    @DisplayName("pulled-rendering-counts-as-identical: the storage layer's rendering is the same document")
    void pulledRendering_WhenResavedAtAStaleVersion_CountsAsIdentical() throws Exception {
        UUID id = UUID.randomUUID();
        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT, CONTENT_VERSION, null),
                false).andExpect(status().isCreated());

        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT, CONTENT_VERSION, null),
                true).andExpect(status().isOk());

        long stored = storedRow(id).getSyncVersion();
        String pulled = readBack(id).get("content").asText();

        assertThat(pulled)
                .as("the case only tests anything while the stored rendering differs from the sent bytes")
                .isNotEqualTo(TestDataFactory.VALID_CONTENT);

        send(id, request(id, BASE_TITLE, pulled, PlannerStatus.DRAFT, CONTENT_VERSION, stored - 1), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncVersion").value(stored));
    }

    @Test
    @DisplayName("stale-title-change-still-conflicts: a moved scalar is still a conflict")
    void staleTitleChange_WhenContentIsTreeEqual_StillConflicts() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, request(id, "A Different Title", TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT,
                        CONTENT_VERSION, stored - 1), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                .andExpect(jsonPath("$.serverVersion").value(stored));
    }

    @Test
    @DisplayName("stale-different-content-conflicts: a document that is not tree-equal is still a conflict")
    void staleDifferentContent_WhenNotTreeEqual_Conflicts() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, request(id, BASE_TITLE, divergentContent(), PlannerStatus.DRAFT, CONTENT_VERSION, stored - 1),
                false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"))
                .andExpect(jsonPath("$.serverVersion").value(stored));
    }

    @Test
    @DisplayName("unparseable-content-falls-through-to-conflict: a document that will not parse conflicts, not 500")
    void unparseableContent_WhenCarriedByAStaleWrite_FallsThroughToConflict() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, request(id, BASE_TITLE, "{\"equipment\":", PlannerStatus.DRAFT, CONTENT_VERSION, stored - 1),
                false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertThat(storedRow(id).getSyncVersion()).isEqualTo(stored);
    }

    @Test
    @DisplayName("no-version-still-conflicts: naming no version is a conflict even when nothing would change")
    void noVersion_WhenPlannerExistsAndNotForced_Conflicts() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT, CONTENT_VERSION, null),
                false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertThat(storedRow(id).getSyncVersion()).isEqualTo(stored);
    }

    @Test
    @DisplayName("force-still-bypasses: a forced write is applied even when it would move nothing")
    void force_WhenCarryingIdenticalStateAtAStaleVersion_StillWrites() throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();

        send(id, request(id, BASE_TITLE, TestDataFactory.VALID_CONTENT, PlannerStatus.DRAFT,
                        CONTENT_VERSION, stored - 1), true)
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
    @DisplayName("the acknowledgement answers exactly when applying the write would move no field")
    void arbitration_WhenComparedWithApplyingTheSameWrite_IsBiconditional(
            String label, String title, ContentChoice choice, PlannerStatus status, int contentVersion)
            throws Exception {
        UUID id = plannerPastItsFirstSave();
        long stored = storedRow(id).getSyncVersion();
        String content = contentFor(choice, id);
        RowState before = stateOf(id);

        int staleStatus = send(id, request(id, title, content, status, contentVersion, stored - 1), false)
                .andReturn().getResponse().getStatus();

        assertThat(staleStatus).isIn(200, 409);
        assertThat(stateOf(id))
                .as("%s: neither an acknowledgement nor a conflict may write", label)
                .isEqualTo(before);

        send(id, request(id, title, content, status, contentVersion, stored), false)
                .andExpect(status().isOk());

        boolean movedAField = !stateOf(id).equals(before);
        assertThat(staleStatus == 200)
                .as("%s: acknowledged=%s but applying the write moved a field=%s",
                        label, staleStatus == 200, movedAField)
                .isEqualTo(!movedAField);
    }
}
