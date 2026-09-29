package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.searchindex.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.support.CatalogEffects;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog sync drains pending planner-catalog-changed rows one planner at a time and derives
 * filter rows, the withdrawal marker and the delete tombstone from the planner's committed row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerCatalogSyncIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerCatalogSyncService catalogSyncService;

    @Autowired
    private PlannerPublishingService publishingService;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private PlannerContentEntityExtractor entityExtractor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User owner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        owner = TestDataFactory.createTestUser(userRepository, "catalog-sync-owner@example.com");
    }

    @Test
    @DisplayName("a publish is indexed and unmarked by the next pass")
    void catalogSync_WhenAPlannerIsPublished_IndexesItAndClearsTheMarker() throws Exception {
        Planner draft = TestDataFactory.planner(owner).title("Sync Publish").save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(draft.getId()).build());
        stringRedisTemplate.opsForValue().set(markerKey(draft.getId()), "1");

        publishingService.publish(owner.getId(), draft.getId());
        CatalogEffects.drain(catalogSyncService, jdbc, draft.getId());

        assertThat(indexedEntities(draft.getId())).isEqualTo(oracleEntities(draft.getId()));
        assertThat(isMarked(draft.getId())).isFalse();
    }

    @Test
    @DisplayName("an unpublish is cleared and marked by the next pass")
    void catalogSync_WhenAPlannerIsUnpublished_ClearsItsRowsAndMarksIt() {
        Planner planner = publishClean("Sync Unpublish");

        publishingService.unpublish(owner.getId(), planner.getId());
        CatalogEffects.drain(catalogSyncService, jdbc, planner.getId());

        assertThat(indexedEntities(planner.getId())).isEmpty();
        assertThat(isMarked(planner.getId())).isTrue();
        assertThat(isTombstoned(planner.getId())).isFalse();
    }

    @Test
    @DisplayName("a deactivated owner's planner is cleared and marked, and reindexed and unmarked on reactivation")
    void catalogSync_WhenTheOwnerDeactivatesAndReactivates_ClearsThenRebuilds() throws Exception {
        Planner planner = publishClean("Sync Owner Away");

        lifecycleService.deleteAccount(owner.getId());
        CatalogEffects.drain(catalogSyncService, jdbc, planner.getId());
        assertThat(indexedEntities(planner.getId())).isEmpty();
        assertThat(isMarked(planner.getId())).isTrue();

        lifecycleService.reactivateAccount(owner.getId());
        CatalogEffects.drain(catalogSyncService, jdbc, planner.getId());
        assertThat(indexedEntities(planner.getId())).isEqualTo(oracleEntities(planner.getId()));
        assertThat(isMarked(planner.getId())).isFalse();
    }

    @Test
    @DisplayName("a delete is tombstoned before it returns and cleared, marked and tombstoned by the next pass")
    void catalogSync_WhenAPlannerIsDeleted_TombstonesItAtOnceAndAgainFromThePass() {
        Planner planner = publishClean("Sync Delete");

        commandService.deletePlanner(owner.getId(), planner.getId());
        assertThat(isTombstoned(planner.getId())).as("the delete masks the planner before it returns").isTrue();
        stringRedisTemplate.delete("del:planner:" + planner.getId());

        CatalogEffects.drain(catalogSyncService, jdbc, planner.getId());

        assertThat(indexedEntities(planner.getId())).isEmpty();
        assertThat(isMarked(planner.getId())).isTrue();
        assertThat(isTombstoned(planner.getId())).as("the pass writes the tombstone again").isTrue();
    }

    @Test
    @DisplayName("three events for one planner in one pass cost one sync and are all marked done")
    void drainPending_WhenAPlannerHasThreeEvents_SyncsItOnceAndMarksAllThree() {
        Planner planner = publishClean("Sync Burst");
        List<Long> ids = List.of(recordEvent(planner.getId()), recordEvent(planner.getId()),
                recordEvent(planner.getId()));

        CatalogEffects.drain(catalogSyncService, jdbc, planner.getId());

        ids.forEach(id -> assertThat(dispatchedAt(id)).as("event %s", id).isNotNull());
        assertThat(ids.stream().map(this::dispatchedAt).distinct().toList())
                .as("one sync marked all three rows in a single statement")
                .hasSize(1);
    }

    @Test
    @DisplayName("a planner whose sync throws stays pending with an attempt counted while the next planner syncs, and the next pass repairs it")
    void drainPending_WhenOnePlannersSyncThrows_KeepsItPendingSyncsTheNextAndRepairsItLater() throws Exception {
        Planner failing = publishClean("Sync Throws");
        Planner withdrawn = publishClean("Sync Withdrawn Neighbour");
        jdbc.update("UPDATE planner_publication SET published = FALSE WHERE planner_id = UUID_TO_BIN(?)",
                withdrawn.getId().toString());
        long failingEvent = recordEvent(failing.getId());
        long withdrawnEvent = recordEvent(withdrawn.getId());
        Object procedure = jdbc.queryForMap("SHOW CREATE PROCEDURE rebuild_planner_filters_scoped")
                .get("Create Procedure");

        jdbc.execute("DROP PROCEDURE rebuild_planner_filters_scoped");
        try {
            CatalogEffects.drainUntil(catalogSyncService,
                    () -> attempts(failingEvent) > 0 && dispatchedAt(withdrawnEvent) != null,
                    "a pass over both planners");
        } finally {
            jdbc.execute(procedure.toString());
        }

        assertThat(dispatchedAt(failingEvent)).isNull();
        assertThat(attempts(failingEvent)).as("the failed sync counted an attempt").isPositive();
        assertThat(dispatchedAt(withdrawnEvent)).isNotNull();
        assertThat(isMarked(withdrawn.getId())).isTrue();

        CatalogEffects.drain(catalogSyncService, jdbc, failing.getId());

        assertThat(dispatchedAt(failingEvent)).as("the next pass repairs the failed planner").isNotNull();
        assertThat(indexedEntities(failing.getId())).isEqualTo(oracleEntities(failing.getId()));
    }

    @Test
    @DisplayName("an event recorded after a pass read its batch stays pending for the next pass")
    void syncPlanner_WhenAnEventArrivesAfterThePassReadItsBatch_LeavesItPending() {
        Planner planner = publishClean("Sync Mid Pass");
        long read = recordEvent(planner.getId());
        long arrivedLater = recordEvent(planner.getId());

        catalogSyncService.syncPlanner(planner.getId(), read);

        assertThat(dispatchedAt(read)).isNotNull();
        assertThat(dispatchedAt(arrivedLater)).isNull();
    }

    private Planner publishClean(String title) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(Set.of("Sinking"))
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        catalogSyncService.syncPlanner(planner.getId(), 0L);
        return planner;
    }

    private long recordEvent(UUID plannerId) {
        jdbc.update("INSERT INTO domain_events (event_type, aggregate_id, payload) "
                + "VALUES ('PLANNER_CATALOG_CHANGED', UUID_TO_BIN(?), '{}')", plannerId.toString());
        return jdbc.queryForObject("SELECT MAX(id) FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?)",
                Long.class, plannerId.toString());
    }

    private Timestamp dispatchedAt(long eventId) {
        return jdbc.queryForObject("SELECT dispatched_at FROM domain_events WHERE id = ?", Timestamp.class, eventId);
    }

    private int attempts(long eventId) {
        return jdbc.queryForObject("SELECT attempts FROM domain_events WHERE id = ?", Integer.class, eventId);
    }

    private Set<String> indexedEntities(UUID plannerId) {
        return Set.copyOf(jdbc.queryForList("SELECT CONCAT(entity_type, ':', entity_id) FROM planner_entity_filter "
                + "WHERE planner_id = UUID_TO_BIN(?)", String.class, plannerId.toString()));
    }

    private Set<String> oracleEntities(UUID plannerId) throws Exception {
        Map<String, Object> row = jdbc.queryForMap("SELECT content, category FROM planner_content "
                + "WHERE planner_id = UUID_TO_BIN(?)", plannerId.toString());
        return entityExtractor.extract(objectMapper.readTree(row.get("content").toString()),
                        MDCategory.fromValue(row.get("category").toString()))
                .stream()
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());
    }

    private boolean isMarked(UUID plannerId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(markerKey(plannerId)));
    }

    private boolean isTombstoned(UUID plannerId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("del:planner:" + plannerId));
    }

    private static String markerKey(UUID plannerId) {
        return "del:published-planner:" + plannerId;
    }
}
