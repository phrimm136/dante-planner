package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.util.PlannerContentSanitizer;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.AuthCookies.performAuthed;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The digest identifies the document the write path received — the output of the request DTO's
 * {@code @Sanitized(PLANNER_CONTENT)} normalization, not the raw request body — and is carried
 * forward untouched.
 *
 * <p>MySQL re-serializes a JSON column, so the {@code content} a reader gets back is not the string
 * the writer sent. A consumer that hashes what it received and expects the digest to match is
 * therefore wrong, and these tests are that claim in executable form. The last case covers the
 * inverse: a client resaving exactly what it pulled gets a digest over that string, because every
 * save carrying a document re-derives it.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerContentDigestLineageIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String token;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "digest-lineage@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        plannerId = UUID.randomUUID();
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions upsert(String content, Long syncVersion) throws Exception {
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                plannerId.toString(),
                "5F",
                "Digest Lineage",
                PlannerStatus.DRAFT,
                content,
                7,
                PlannerType.MIRROR_DUNGEON,
                syncVersion,
                null);

        return performAuthed(mockMvc, put("/api/planner/md/{id}", plannerId).with(withCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), token);
    }

    private void save(String content) throws Exception {
        upsert(content, null).andExpect(status().isCreated());
    }

    private JsonNode resave(String content, long syncVersion) throws Exception {
        String json = upsert(content, syncVersion)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode readBack() throws Exception {
        String json = performAuthed(mockMvc, get("/api/planner/md/{id}", plannerId), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void contentDigest_WhenReadBackAfterASave_HashesTheRequestBytes() throws Exception {
        save(TestDataFactory.VALID_CONTENT);

        assertThat(readBack().get("contentDigest").asText())
                .isEqualTo(sha256Hex(TestDataFactory.VALID_CONTENT));
    }

    @Test
    void contentDigest_WhenReadBackAfterASave_DoesNotHashTheReturnedDocument() throws Exception {
        save(TestDataFactory.VALID_CONTENT);

        JsonNode response = readBack();
        String returnedContent = response.get("content").asText();

        assertThat(returnedContent)
                .as("the stored form is MySQL's re-serialization, not the bytes the author sent")
                .isNotEqualTo(TestDataFactory.VALID_CONTENT);
        assertThat(response.get("contentDigest").asText())
                .as("a consumer that re-hashes what it received gets a different value")
                .isNotEqualTo(sha256Hex(returnedContent));
    }

    @Test
    void contentDigest_WhenTheRequestCarriesSlackWhitespace_HashesTheSanitizerOutput() throws Exception {
        String spaced = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readTree(TestDataFactory.VALID_CONTENT));
        String normalized = PlannerContentSanitizer.sanitize(spaced);

        assertThat(normalized)
                .as("the fixture only tests anything while bind-time normalization changes the string")
                .isNotEqualTo(spaced);

        save(spaced);
        String digest = readBack().get("contentDigest").asText();

        assertThat(digest)
                .as("the digest identifies the document the write path received, after sanitization")
                .isEqualTo(sha256Hex(normalized));
        assertThat(digest)
                .as("the raw request body is not what the digest identifies")
                .isNotEqualTo(sha256Hex(spaced));
    }

    @Test
    void contentDigest_WhenAPulledDocumentIsResavedVerbatim_HashesTheResentString() throws Exception {
        save(TestDataFactory.VALID_CONTENT);

        JsonNode pulled = readBack();
        String pulledContent = pulled.get("content").asText();

        JsonNode resaved = resave(pulledContent, pulled.get("syncVersion").asLong());

        assertThat(resaved.get("contentDigest").asText())
                .as("a client that resaves what it pulled must get back a digest over that string, "
                        + "or it can never confirm the server holds what it holds")
                .isEqualTo(sha256Hex(PlannerContentSanitizer.sanitize(pulledContent)));
        assertThat(resaved.get("contentDigest").asText())
                .isNotEqualTo(pulled.get("contentDigest").asText());
    }
}
