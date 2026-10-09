package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.searchindex.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerCatalogSyncConcurrencyIT extends ExclusiveDatabaseSupport {

    private static final int ROUNDS = 20;
    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long JOIN_TIMEOUT_MS = 60_000;

    private static final String SECOND_CONTENT = TestDataFactory.VALID_CONTENT
            .replace("\"selectedGiftIds\":[\"9001\"]", "\"selectedGiftIds\":[\"9003\"]")
            .replace("{\"id\":\"10101\"", "{\"id\":\"10102\"");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogSyncService catalogSyncService;

    @Autowired
    private PlannerContentEntityExtractor entityExtractor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User owner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        emptyPlannerTables(jdbc);
        owner = TestDataFactory.createTestUser(userRepository, "catalog-sync-concurrency@example.com");
    }

    @Test
    void syncPlanner_WhenTwoNeverSyncedPlannersShareAnIndexGap_BothCommitEveryRound() throws Exception {
        long high = ThreadLocalRandom.current().nextLong();
        long low = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE / 2);
        UUID first = publish(new UUID(high, low + 2));
        UUID second = publish(new UUID(high, low + 3));
        for (UUID neighbour : List.of(new UUID(high, low), new UUID(high, low + 10))) {
            catalogSyncService.syncPlanner(publish(neighbour), List.of());
        }
        Set<String> expected = oracleEntities(first);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        for (int round = 0; round < ROUNDS && failures.isEmpty(); round++) {
            clearFilterRows(first, second);
            CyclicBarrier start = new CyclicBarrier(2);
            Thread one = syncThread(first, start, failures);
            Thread two = syncThread(second, start, failures);
            one.start();
            two.start();
            one.join(JOIN_TIMEOUT_MS);
            two.join(JOIN_TIMEOUT_MS);
            assertThat(one.isAlive() || two.isAlive()).as("round %d finished", round).isFalse();
        }

        assertThat(failures).as("no sync lost a lock conflict to its neighbour").isEmpty();
        assertThat(indexedEntities(first)).isEqualTo(expected);
        assertThat(indexedEntities(second)).isEqualTo(expected);
    }

    @Test
    void syncPlanner_WhenTheContentChangesBetweenTwoConcurrentSyncs_CommitsEveryRoundWithOnlyTheFinalContentsRows()
            throws Exception {
        UUID planner = publish(UUID.randomUUID());
        catalogSyncService.syncPlanner(planner, List.of());
        String original = contentOf(planner);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        for (int round = 0; round < ROUNDS && failures.isEmpty(); round++) {
            String next = round % 2 == 0 ? SECOND_CONTENT : original;
            CyclicBarrier start = new CyclicBarrier(2);
            Thread syncOnly = syncThread(planner, start, failures);
            Thread writeThenSync = new Thread(() -> {
                if (awaited(start, failures)) {
                    try {
                        jdbc.update("UPDATE planner_content SET content = ? WHERE planner_id = UUID_TO_BIN(?)",
                                next, planner.toString());
                        catalogSyncService.syncPlanner(planner, List.of());
                    } catch (RuntimeException e) {
                        failures.add(e);
                    }
                }
            }, "write-then-sync");
            writeThenSync.setDaemon(true);
            syncOnly.start();
            writeThenSync.start();
            syncOnly.join(JOIN_TIMEOUT_MS);
            writeThenSync.join(JOIN_TIMEOUT_MS);
            assertThat(syncOnly.isAlive() || writeThenSync.isAlive()).as("round %d finished", round).isFalse();

            assertThat(indexedEntities(planner)).as("round %d", round).isEqualTo(oracleEntities(planner));
        }

        assertThat(failures).isEmpty();
    }

    @Test
    void drainPending_WhenItSyncsAPlanner_RunsTheSyncAtReadCommitted() throws Exception {
        UUID planner = publish(UUID.randomUUID());
        jdbc.update("INSERT INTO domain_events (event_type, aggregate_id, payload) "
                + "VALUES ('PLANNER_CATALOG_CHANGED', UUID_TO_BIN(?), '{}')", planner.toString());

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "SELECT planner_id FROM planner_content WHERE planner_id = UUID_TO_BIN(?) FOR UPDATE")) {
                lock.setString(1, planner.toString());
                lock.executeQuery().close();
            }

            CompletableFuture<Integer> pass = CompletableFuture.supplyAsync(catalogSyncService::drainPending);
            try {
                assertThat(waitingIsolationLevel()).isEqualTo("READ COMMITTED");
            } finally {
                holder.rollback();
                pass.get(60, TimeUnit.SECONDS);
            }
        }
    }

    private Thread syncThread(UUID plannerId, CyclicBarrier start, List<Throwable> failures) {
        Thread thread = new Thread(() -> {
            if (awaited(start, failures)) {
                try {
                    catalogSyncService.syncPlanner(plannerId, List.of());
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            }
        }, "sync-" + plannerId);
        thread.setDaemon(true);
        return thread;
    }

    private static boolean awaited(CyclicBarrier start, List<Throwable> failures) {
        try {
            start.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(e);
            return false;
        } catch (BrokenBarrierException | TimeoutException e) {
            failures.add(e);
            return false;
        }
    }

    private String waitingIsolationLevel() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (true) {
            List<String> levels = jdbc.queryForList("""
                    SELECT t.trx_isolation_level FROM information_schema.innodb_trx t
                    JOIN information_schema.processlist p ON p.id = t.trx_mysql_thread_id
                    WHERE t.trx_state = 'LOCK WAIT' AND p.db = DATABASE()
                    """, String.class);
            if (!levels.isEmpty()) {
                return levels.getFirst();
            }
            assertThat(Instant.now()).as("the sync never waited on the held content row").isBefore(deadline);
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    private UUID publish(UUID id) {
        Planner planner = TestDataFactory.planner(owner)
                .id(id)
                .selectedKeywords(Set.of("Sinking", "Burn"))
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        return planner.getId();
    }

    private void clearFilterRows(UUID... plannerIds) {
        for (UUID plannerId : plannerIds) {
            jdbc.update("DELETE FROM planner_entity_filter WHERE planner_id = UUID_TO_BIN(?)", plannerId.toString());
            jdbc.update("DELETE FROM planner_keyword_filter WHERE planner_id = UUID_TO_BIN(?)", plannerId.toString());
        }
    }

    private String contentOf(UUID plannerId) {
        return jdbc.queryForObject("SELECT content FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, plannerId.toString());
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
}
