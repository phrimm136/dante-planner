package org.danteplanner.backend.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-3 co-acceptance test (INV7): a re-check miss-flood on the dedicated bulkhead pool never
 * delays a concurrent write.
 *
 * <p>The bulkhead datasource is pointed through the Toxiproxy app→primary proxy and slowed with the
 * {@code wan} latency toxic, so every replica-miss re-check holds a bulkhead connection for the
 * toxic's dwell. A flood of junk-UUID reads (each a replica miss that re-checks the wan-slowed
 * bulkhead) saturates the {@link PoolLedger#BULKHEAD_POOL 3-connection} pool and backs its queue up.
 * The write path draws from the separate, un-proxied primary write pool, so a concurrent write must
 * complete without being serialized behind the flood.</p>
 *
 * <p>Determinism (INV4): INV7 is asserted as <em>comparative isolation</em>, never an absolute
 * latency SLA. A single wan-slowed re-check is timed first as an in-test reference; the concurrent
 * write must beat that one re-check (a ratio, not a tuned constant) and must return while flood
 * tasks are still in flight. The only wall-clock number is the reference re-check, and it is a
 * measured baseline, not a threshold. No timing constant lives in production code.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class BulkheadIT extends CausalHarnessSupport {

    private static final Logger log = LoggerFactory.getLogger(BulkheadIT.class);

    private static final int PROXY_LISTEN_PORT = 8666;

    private static final String WRITE_PROBE_TABLE = "bulkhead_write_probe";
    private static final String WRITE_PROBE_VALUE = "written-during-flood";

    private static final int FLOOD_SIZE = PoolLedger.BULKHEAD_POOL * 8;

    @Autowired
    private ByIdReadGuard byIdReadGuard;

    @Autowired
    private PlannerQueryService plannerQueryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", REPLICA::getJdbcUrl);
        registry.add("datasource.replica.username", REPLICA::getUsername);
        registry.add("datasource.replica.password", REPLICA::getPassword);
        registry.add("datasource.bulkhead.url", () -> "jdbc:mysql://"
                + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(PROXY_LISTEN_PORT) + "/testdb");
        registry.add("datasource.bulkhead.username", () -> "test");
        registry.add("datasource.bulkhead.password", () -> "test");
    }

    @Test
    @DisplayName("INV7: a wan-slowed re-check miss-flood saturating the bulkhead never delays a concurrent write on the separate primary write pool")
    void reCheckFloodOnBulkhead_doesNotDelay_concurrentWrite() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "bulkhead-inv7@example.com");
        Long userId = owner.getId();
        replicationControl.awaitCaughtUp();

        primaryJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + WRITE_PROBE_TABLE + " (id INT PRIMARY KEY, val VARCHAR(64))");
        primaryJdbcTemplate.update("DELETE FROM " + WRITE_PROBE_TABLE);

        ExecutorService flood = Executors.newFixedThreadPool(FLOOD_SIZE);
        try {
            toxiproxyControl.applyWan();

            long singleReCheckMs = timeMillis(() -> junkReCheck(userId));

            CountDownLatch started = new CountDownLatch(FLOOD_SIZE);
            ConcurrentLinkedQueue<String> floodOutcomes = new ConcurrentLinkedQueue<>();
            List<Future<?>> floodTasks = new ArrayList<>(FLOOD_SIZE);
            long floodStart = System.nanoTime();
            for (int i = 0; i < FLOOD_SIZE; i++) {
                floodTasks.add(flood.submit(() -> {
                    started.countDown();
                    floodOutcomes.add(junkReCheck(userId));
                }));
            }
            started.await();

            long writeElapsedMs = timeMillis(this::writeViaPrimaryPool);
            long stillInFlight = floodTasks.stream().filter(f -> !f.isDone()).count();

            for (Future<?> task : floodTasks) {
                task.get();
            }
            long floodDrainMs = (System.nanoTime() - floodStart) / 1_000_000;

            log.info("INV7 magnitudes: singleReCheckMs={} writeElapsedMs={} stillInFlight={} floodDrainMs={} outcomes={}",
                    singleReCheckMs, writeElapsedMs, stillInFlight, floodDrainMs, distinct(floodOutcomes));

            assertThat(floodOutcomes)
                    .as("every junk-UUID re-check must be a genuine slow miss on the wan-slowed bulkhead, "
                            + "not a fast connection error masquerading as a fast flood")
                    .hasSize(FLOOD_SIZE)
                    .containsOnly(PlannerNotFoundException.class.getSimpleName());

            assertThat(floodDrainMs)
                    .as("a %d-task flood through the %d-connection bulkhead must queue and take longer "
                            + "than one re-check — proving the bulkhead actually saturated",
                            FLOOD_SIZE, PoolLedger.BULKHEAD_POOL)
                    .isGreaterThan(singleReCheckMs);

            assertThat(readWriteProbeFromPrimary())
                    .as("the concurrent write must persist to the primary write pool")
                    .isEqualTo(WRITE_PROBE_VALUE);

            assertThat(writeElapsedMs)
                    .as("INV7: the write draws from the separate primary write pool and must NOT be "
                            + "serialized behind the saturated bulkhead — it must beat even a single "
                            + "wan-slowed re-check (%dms)", singleReCheckMs)
                    .isLessThan(singleReCheckMs);

            assertThat(stillInFlight)
                    .as("INV7 structural: the write must return while the bulkhead flood is still draining")
                    .isGreaterThan(0L);
        } finally {
            flood.shutdownNow();
            toxiproxyControl.removeWan();
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    /**
     * Runs one junk-UUID read through the seam: a read-only replica miss that re-checks the
     * wan-slowed bulkhead. Returns the simple name of the miss it surfaces (the flood treats these
     * as load, not assertions).
     */
    private String junkReCheck(Long userId) {
        UUID junk = UUID.randomUUID();
        try {
            byIdReadGuard.read(
                    ByIdReadGuard.PLANNER_ENTITY_TYPE,
                    junk,
                    () -> plannerQueryService.getPlanner(userId, junk));
            return "no-miss";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    private void writeViaPrimaryPool() {
        TransactionTemplate tt = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tt.setReadOnly(false);
        tt.execute(status -> new JdbcTemplate(dataSource).update(
                "INSERT INTO " + WRITE_PROBE_TABLE + " (id, val) VALUES (1, ?)", WRITE_PROBE_VALUE));
    }

    private String readWriteProbeFromPrimary() {
        return primaryJdbcTemplate.queryForObject(
                "SELECT val FROM " + WRITE_PROBE_TABLE + " WHERE id = 1", String.class);
    }

    private static List<String> distinct(ConcurrentLinkedQueue<String> outcomes) {
        return outcomes.stream().distinct().toList();
    }

    private static long timeMillis(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
