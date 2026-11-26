package org.danteplanner.backend.repository;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.repository.ContentWrite;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;
import org.danteplanner.backend.support.PlannerContentRows;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerContentWriteIT extends SharedMySqlContainerSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Instant SAVED_AT = Instant.parse("2026-11-26T01:02:03.456789Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerContentRepository contentRepository;

    @Autowired
    private PlannerKeywordBackfillRepository backfillRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "content-write@example.com");
        planner = TestDataFactory.planner(owner).syncVersion(3).save(plannerRepository);
    }

    private ContentWrite renamed(long syncVersion) {
        StoredContent stored = planner.content();
        return new ContentWrite("Renamed", PlannerStatus.SAVED, "10F", Set.of("Burst"), stored.json(),
                stored.schemaVersion(), stored.gameContentVersion(), UUID.randomUUID(), syncVersion, SAVED_AT);
    }

    private int inTransaction(Supplier<Integer> write) {
        return transactionTemplate.execute(status -> write.get());
    }

    private static void assertSameRow(StoredPlanner actual, StoredPlanner expected) {
        assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().ignoringFields("content.json").isEqualTo(expected);
        assertThat(MAPPER.readTree(actual.content().json()))
                .as("MySQL re-serializes a JSON column, so the document is compared, not its text")
                .isEqualTo(MAPPER.readTree(expected.content().json()));
    }

    private Map<String, Object> row() {
        return jdbcTemplate.queryForMap("SELECT HEX(planner_id) AS planner_id, title, status, category,"
                + " selected_keywords, content, content_schema_version, game_content_version, sync_version,"
                + " row_lock_version, HEX(device_id) AS device_id, last_modified_at, deleted_at"
                + " FROM planner_content WHERE planner_id = UUID_TO_BIN(?)", planner.id().toString());
    }

    @Test
    void replaceAtVersion_WhenTheReadVersionIsStale_MatchesNoRowAndLeavesTheRowIdentical() {
        Map<String, Object> before = row();

        int matched = inTransaction(() -> contentRepository.replaceAtVersion(planner.id(), 2L, renamed(3L)));

        assertThat(matched).isZero();
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void replaceAtVersion_WhenTheReadVersionIsCurrent_WritesEveryColumnTheSaveCarries() {
        ContentWrite write = renamed(4L);

        int matched = inTransaction(() -> contentRepository.replaceAtVersion(planner.id(), 3L, write));

        assertThat(matched).isOne();
        assertSameRow(plannerRepository.findAggregate(planner.id()).orElseThrow(), planner.saved(write));
    }

    @Test
    void replaceAtVersion_WhenTheRowIsATombstone_MatchesNoRow() {
        PlannerContentRows.markDeleted(jdbcTemplate, planner.id());
        Map<String, Object> before = row();

        int matched = inTransaction(() -> contentRepository.replaceAtVersion(planner.id(), 3L, renamed(4L)));

        assertThat(matched).isZero();
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void migrateContent_WhenTheReadVersionIsCurrent_ReplacesTheDocumentAndKeepsTheVersion() {
        String migrated = TestDataFactory.withSelectedKeywords(planner.content().json(), Set.of("Sinking"));

        int matched = inTransaction(() -> contentRepository.migrateContent(planner.id(), 3L, migrated, SAVED_AT));

        assertThat(matched).isOne();
        assertSameRow(plannerRepository.findAggregate(planner.id()).orElseThrow(), planner.migrated(migrated, SAVED_AT));
    }

    @Test
    void migrateContent_WhenTheReadVersionIsStale_MatchesNoRow() {
        Map<String, Object> before = row();

        int matched = inTransaction(() -> contentRepository.migrateContent(planner.id(), 2L, "{}", SAVED_AT));

        assertThat(matched).isZero();
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void markDeleted_WhenTheRowIsAlreadyATombstone_MatchesNoRow() {
        assertThat(inTransaction(() -> contentRepository.markDeleted(planner.id(), SAVED_AT))).isOne();

        assertThat(inTransaction(() -> contentRepository.markDeleted(planner.id(), SAVED_AT))).isZero();
        assertThat(row().get("deleted_at")).isNotNull();
    }

    @Test
    void refreshContentKeywords_WhenTheVersionMovedSinceThePageRead_MatchesNoRow() {
        Map<String, Object> before = row();

        int matched = backfillRepository.refreshContentKeywords(planner.id(), 2L, "[\"Burst\"]");

        assertThat(matched).isZero();
        assertThat(row()).isEqualTo(before);
        assertThat(backfillRepository.refreshContentKeywords(planner.id(), 3L, "[\"Burst\"]")).isOne();
    }
}
