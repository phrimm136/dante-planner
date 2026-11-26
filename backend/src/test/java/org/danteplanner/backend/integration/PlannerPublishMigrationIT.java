package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlannerPublishMigrationIT extends ExclusiveDatabaseSupport {

    private static final String GIFT_RENAME = "{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}";

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

    private User owner;
    private String token;

    // Spring resolves the supplier more than once, and each call would make a directory.
    private static final Path RENAMING_DATA_DIR = gameDataRenaming(GIFT_RENAME);

    @DynamicPropertySource
    static void renamingGameData(DynamicPropertyRegistry registry) {
        registry.add("game.data.path", RENAMING_DATA_DIR::toString);
    }

    @AfterAll
    static void deleteRenamingDataDir() throws IOException {
        try (Stream<Path> links = Files.list(RENAMING_DATA_DIR)) {
            for (Path link : links.toList()) {
                Files.delete(link);
            }
        }
        Files.delete(RENAMING_DATA_DIR);
    }

    private static Path gameDataRenaming(String idMigrations) {
        try {
            Path dataDir = Files.createTempDirectory("game-data-renaming");
            try (Stream<Path> entries = Files.list(Path.of("../static/data"))) {
                for (Path entry : entries.toList()) {
                    Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
                }
            }
            Files.writeString(dataDir.resolve(GameDataRegistry.ID_MIGRATIONS_FILE), idMigrations);
            return dataDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "publish-migration-owner@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
    }

    @Test
    void publish_WhenStoredContentHoldsARenamedGift_StoresAndAnswersTheNewIdAtTheSameVersion() throws Exception {
        String stored = TestDataFactory.VALID_CONTENT.replace(
                "\"observationGiftIds\":[]", "\"observationGiftIds\":[\"19247\"]");
        StoredPlanner planner = TestDataFactory.planner(owner).published(false).content(stored)
                .save(plannerRepository);
        long version = planner.content().syncVersion();

        String body = mockMvc.perform(post("/api/planner/md/{id}/publish", planner.id())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        StoredPlanner after = stored(planner.id());
        JsonNode renamed = objectMapper.createArrayNode().add("19002");
        assertThat(objectMapper.readTree(after.content().json()).path("observationGiftIds")).isEqualTo(renamed);
        assertThat(objectMapper.readTree(response.get("content").asString()).path("observationGiftIds"))
                .isEqualTo(renamed);
        assertThat(after.content().syncVersion()).isEqualTo(version);
        assertThat(response.get("syncVersion").asLong()).isEqualTo(version);
        assertThat(after.publication().isPublished()).isTrue();
    }

    private StoredPlanner stored(UUID id) {
        return plannerRepository.findAggregateForOwner(id, owner.getId()).orElseThrow();
    }
}
