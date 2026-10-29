package org.danteplanner.backend.integration;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.searchindex.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.TransactionSpans;
import org.danteplanner.backend.support.TransactionSpans.Span;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import java.util.stream.IntStream;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerFilterRowsConcurrencyIT extends ExclusiveDatabaseSupport {

    private static final int ROUNDS = 500;
    private static final int MIN_OVERLAPPING_ROUNDS = ROUNDS / 2;
    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long JOIN_TIMEOUT_MS = 60_000;

    private static final String SECOND_CONTENT = IntStream.rangeClosed(1, 8).boxed().reduce(
            TestDataFactory.VALID_CONTENT
                    .replace("\"selectedGiftIds\":[\"9001\"]", "\"selectedGiftIds\":[\"9003\"]"),
            (content, sinner) -> content
                    .replace("{\"id\":\"1%02d01\"".formatted(sinner), "{\"id\":\"1%02d02\"".formatted(sinner))
                    .replace("{\"id\":\"2%02d01\"".formatted(sinner), "{\"id\":\"2%02d02\"".formatted(sinner)),
            (left, right) -> right);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerPublishingService publishingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        owner = TestDataFactory.createTestUser(userRepository, "filter-rows-concurrency@example.com");
    }

    @Test
    void syncFilterRows_WhenTwoNeverSyncedPlannersShareAnIndexGap_BothCommitEveryRound() throws Exception {
        long high = ThreadLocalRandom.current().nextLong();
        long low = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE / 2);
        UUID first = publish(new UUID(high, low + 2));
        UUID second = publish(new UUID(high, low + 3));
        for (UUID neighbour : List.of(new UUID(high, low), new UUID(high, low + 10))) {
            FilterRowsSync.syncFilterRows(transactionManager, catalogService, publish(neighbour));
        }
        Set<String> expected = oracleEntities(first);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        int overlapped = 0;
        try (TransactionSpans.Installed unused = TransactionSpans.install(transactionManager)) {
            for (int round = 0; round < ROUNDS && failures.isEmpty(); round++) {
                clearFilterRows(first, second);
                CyclicBarrier start = new CyclicBarrier(2);
                Span oneSpan = new Span();
                Span twoSpan = new Span();
                Thread one = syncThread(first, start, oneSpan, failures);
                Thread two = syncThread(second, start, twoSpan, failures);
                one.start();
                two.start();
                one.join(JOIN_TIMEOUT_MS);
                two.join(JOIN_TIMEOUT_MS);
                assertThat(one.isAlive() || two.isAlive()).as("round %d finished", round).isFalse();
                if (oneSpan.overlaps(twoSpan)) {
                    overlapped++;
                }
            }
        }

        assertThat(failures).as("no rebuild lost a lock conflict to its neighbour").isEmpty();
        System.out.println("filter rows race: " + overlapped + " of " + ROUNDS + " rounds overlapped");
        assertThat(overlapped).as("rounds whose rebuild transactions overlapped").isGreaterThanOrEqualTo(
                MIN_OVERLAPPING_ROUNDS);
        assertThat(indexedEntities(first)).isEqualTo(expected);
        assertThat(indexedEntities(second)).isEqualTo(expected);
    }

    @Test
    void syncFilterRows_WhenTheContentChangesBetweenTwoConcurrentRebuilds_CommitsEveryRoundWithOnlyTheFinalContentsRows()
            throws Exception {
        UUID planner = publish(UUID.randomUUID());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner);
        String original = contentOf(planner);
        Set<String> originalRows = oracleEntities(planner);
        jdbc.update("UPDATE planner_content SET content = ? WHERE planner_id = UUID_TO_BIN(?)",
                SECOND_CONTENT, planner.toString());
        Set<String> secondRows = oracleEntities(planner);
        jdbc.update("UPDATE planner_content SET content = ? WHERE planner_id = UUID_TO_BIN(?)",
                original, planner.toString());
        Set<String> changed = new HashSet<>(originalRows);
        changed.removeAll(secondRows);
        assertThat(changed.size() * 2)
                .as("each round's content change swaps about half the key set")
                .isGreaterThanOrEqualTo(originalRows.size() - 4);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        int overlapped = 0;
        try (TransactionSpans.Installed unused = TransactionSpans.install(transactionManager)) {
            for (int round = 0; round < ROUNDS && failures.isEmpty(); round++) {
                String next = round % 2 == 0 ? SECOND_CONTENT : original;
                CyclicBarrier start = new CyclicBarrier(2);
                Span syncSpan = new Span();
                Span writeSpan = new Span();
                Thread syncOnly = syncThread(planner, start, syncSpan, failures);
                Thread writeThenSync = new Thread(() -> {
                    if (awaited(start, failures)) {
                        try {
                            jdbc.update("UPDATE planner_content SET content = ? WHERE planner_id = UUID_TO_BIN(?)",
                                    next, planner.toString());
                            TransactionSpans.within(writeSpan,
                                    () -> FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner));
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
                if (syncSpan.overlaps(writeSpan)) {
                    overlapped++;
                }

                assertThat(indexedEntities(planner)).as("round %d", round).isEqualTo(oracleEntities(planner));
            }
        }

        assertThat(failures).isEmpty();
        System.out.println("filter rows race: " + overlapped + " of " + ROUNDS + " rounds overlapped");
        assertThat(overlapped).as("rounds whose rebuild transactions overlapped").isGreaterThanOrEqualTo(
                MIN_OVERLAPPING_ROUNDS);
    }

    @Test
    void publish_WhenTheContentRowIsLocked_WaitsAtReadCommitted() throws Exception {
        UUID planner = draft(UUID.randomUUID());

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "SELECT planner_id FROM planner_content WHERE planner_id = UUID_TO_BIN(?) FOR UPDATE")) {
                lock.setString(1, planner.toString());
                lock.executeQuery().close();
            }

            CompletableFuture<Void> pass = CompletableFuture.runAsync(
                    () -> publishingService.publish(owner.getId(), planner));
            try {
                assertThat(waitingIsolationLevel()).isEqualTo("READ COMMITTED");
            } finally {
                holder.rollback();
                pass.get(60, TimeUnit.SECONDS);
            }
        }
    }

    private Thread syncThread(UUID plannerId, CyclicBarrier start, Span span, List<Throwable> failures) {
        Thread thread = new Thread(() -> {
            if (awaited(start, failures)) {
                try {
                    TransactionSpans.within(span,
                            () -> FilterRowsSync.syncFilterRows(transactionManager, catalogService, plannerId));
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            }
        }, "rebuild-" + plannerId);
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
            assertThat(Instant.now()).as("the write never waited on the held content row").isBefore(deadline);
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    private UUID publish(UUID id) {
        return planner(id, true);
    }

    private UUID draft(UUID id) {
        return planner(id, false);
    }

    private UUID planner(UUID id, boolean published) {
        Planner planner = TestDataFactory.planner(owner)
                .id(id)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(published)
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
