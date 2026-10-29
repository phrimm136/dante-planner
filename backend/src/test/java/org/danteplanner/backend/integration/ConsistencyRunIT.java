package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.consistency.Calibration;
import org.danteplanner.backend.integration.consistency.CookieJar;
import org.danteplanner.backend.integration.consistency.Coverage;
import org.danteplanner.backend.integration.consistency.CoverageTarget;
import org.danteplanner.backend.integration.consistency.HistoryRecorder;
import org.danteplanner.backend.integration.consistency.HistoryWriter;
import org.danteplanner.backend.integration.consistency.KeyDraw;
import org.danteplanner.backend.integration.consistency.OperationGenerator;
import org.danteplanner.backend.integration.consistency.OregonPrimary;
import org.danteplanner.backend.integration.consistency.PhaseRunner;
import org.danteplanner.backend.integration.consistency.Plan;
import org.danteplanner.backend.integration.consistency.PlannedOperation;
import org.danteplanner.backend.integration.consistency.PlannerPool;
import org.danteplanner.backend.integration.consistency.PlannerSession;
import org.danteplanner.backend.integration.consistency.Region;
import org.danteplanner.backend.integration.consistency.Replication;
import org.danteplanner.backend.integration.consistency.SeedGuard;
import org.danteplanner.backend.integration.consistency.SeedLoop;
import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.consistency.Anomaly;
import org.danteplanner.consistency.ConsistencyChecker;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.Shrinker;
import org.danteplanner.consistency.Verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.AUTHOR_DEVICES;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.BISECTION_DEPTH_BUDGET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.COVERAGE_EXTENSION_CAP;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.CREATOR;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_OPS_PER_SEED;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_SEED_COUNT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_SEED_FROM;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_STOP_AT_FIRST_VIOLATION;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DOUBLING_BUDGET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.EXTENSION_CAP;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.HISTORY_DIRECTORY;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.OPS_PER_SEED;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.PROBE_BATCH_BUDGET;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SEED_COUNT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SEED_FROM;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SETUP_PLANNERS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.STOP_AT_FIRST_VIOLATION;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.historyFile;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.minimalHistoryFile;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.uncoveredDescription;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ConsistencyRunIT extends ReplicaRoutingHarnessSupport {

    private static final KeyDraw ONLY_PLANNER = new KeyDraw(0);
    private static final long OREGON_DEADLINE_MS = 5_000L;
    private static final long OREGON_POLL_INTERVAL_MS = 100L;
    private static final String BREAKER_STATE_GAUGE = "resilience4j.circuitbreaker.state";
    private static final String POOL_IDLE_GAUGE = "hikaricp.connections.idle";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void plannerHistories_WhenSessionsRunUnderReplicationFaults_KeepTheContract() throws Exception {
        long seedFrom = Long.getLong(SEED_FROM, DEFAULT_SEED_FROM);
        int seedCount = Integer.getInteger(SEED_COUNT, DEFAULT_SEED_COUNT);
        int budget = Integer.getInteger(OPS_PER_SEED, DEFAULT_OPS_PER_SEED);
        int extensionCap = Integer.getInteger(EXTENSION_CAP, COVERAGE_EXTENSION_CAP);
        boolean stopAtFirstViolation = Boolean.parseBoolean(
                System.getProperty(STOP_AT_FIRST_VIOLATION, String.valueOf(DEFAULT_STOP_AT_FIRST_VIOLATION)));
        List<Long> seeds = LongStream.range(seedFrom, seedFrom + seedCount).boxed().toList();
        Replication replication = new HarnessReplication(replicationControl, toxiproxyControl);
        Files.createDirectories(HISTORY_DIRECTORY);

        try (OregonContext oregon = OregonContext.start()) {
            Calibration.Result calibration = calibrate(replication);
            System.out.println("consistency calibration: " + calibration);
            OregonPrimary oregonPrimary = new HarnessOregonPrimary(toxiproxyControl, oregon.meterRegistry());
            Coverage coverage = new Coverage(target -> count(registry(target, oregon), target));
            SeedLoop.SeedBody<SeedResult> body = seed -> coverage.measure(() -> runSeed(seed, budget,
                    calibration.latencyMillis(), replication, oregonPrimary, oregon));
            Predicate<SeedResult> stopAfter = result -> stopAtFirstViolation
                    && !result.verdict().violations().isEmpty();
            List<SeedResult> baseResults = SeedLoop.run(seeds, replication, oregonPrimary, body, stopAfter);
            boolean stopped = baseResults.stream().anyMatch(stopAfter);
            List<SeedResult> extraResults = SeedLoop.runUntil(seedFrom + seedCount, extensionCap,
                    () -> !stopped && !coverage.uncovered().isEmpty(), replication, oregonPrimary, seed -> {
                        SeedResult result = body.run(seed);
                        System.out.println("consistency extension: seed " + seed + " → coverage "
                                + coverage.summary());
                        return result;
                    }, stopAfter);
            List<SeedResult> results = Stream.concat(baseResults.stream(), extraResults.stream()).toList();

            results.forEach(result -> {
                System.out.println("consistency seed " + result.seed() + ": "
                        + result.verdict().violations().size() + " violations, anomalies "
                        + anomalyCounts(result.verdict()) + ", faults A " + result.plan().phaseA().fault()
                        + " B " + result.plan().phaseB().fault() + " C " + result.plan().phaseC().fault()
                        + ", history " + result.history().toAbsolutePath());
                System.out.println("consistency seed " + result.seed() + " homes " + homes(result.plan())
                        + ", phase C " + result.phaseC().toMillis() + " ms");
            });
            System.out.println("consistency coverage: " + coverage.summary());
            List<String> violations = results.stream()
                    .flatMap(result -> result.verdict().violations().stream()
                            .map(violation -> "seed " + result.seed() + " (" + result.history().toAbsolutePath()
                                    + "): " + violation))
                    .toList();
            List<String> uncheckedSeeds = results.stream()
                    .flatMap(result -> result.guardFailures().stream()
                            .map(failure -> "seed " + result.seed() + " (" + result.history().toAbsolutePath()
                                    + "): " + failure))
                    .toList();
            assertThat(Stream.concat(uncheckedSeeds.stream(), violations.stream()).toList())
                    .as("seeds whose history cannot show a violation, then violations of the ADR 008 contract, "
                            + "by seed and history file")
                    .isEmpty();
            assertThat(coverage.uncovered())
                    .as(uncoveredDescription(extraResults.size(), coverage))
                    .isEmpty();
        }
    }

    private Calibration.Result calibrate(Replication replication) throws Exception {
        try {
            return Calibration.find(this::probePinsPrimary, PROBE_BATCH_BUDGET, DOUBLING_BUDGET,
                    BISECTION_DEPTH_BUDGET);
        } finally {
            replication.removeLatency();
            replication.awaitCaughtUp();
        }
    }

    private boolean probePinsPrimary(long latencyMillis) throws Exception {
        toxiproxyControl.applyReplicationLatency(latencyMillis);
        User author = TestDataFactory.createTestUser(userRepository,
                "consistency-calibration-" + UUID.randomUUID() + "@example.com");
        Cookie accessToken = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, author));
        PlannerPool pool = new PlannerPool();
        try (HistoryRecorder recorder = new HistoryRecorder(Writer.nullWriter(), System::nanoTime)) {
            PlannerSession session = new PlannerSession(SESSIONS.get(CREATOR),
                    new CookieJar(accessToken, AuthCookies.freshDeviceId()), mockMvc, mockMvc, objectMapper, recorder,
                    pool);
            session.perform(new PlannedOperation.Create());
            double primaryBefore = count(meterRegistry, CoverageTarget.GATE_PINS_PRIMARY);
            double replicaBefore = count(meterRegistry, CoverageTarget.GATE_SERVES_REPLICA);
            session.perform(new PlannedOperation.Read(ONLY_PLANNER));
            boolean pinned = count(meterRegistry, CoverageTarget.GATE_PINS_PRIMARY) > primaryBefore;
            if (!pinned && count(meterRegistry, CoverageTarget.GATE_SERVES_REPLICA) == replicaBefore) {
                throw new IllegalStateException("a calibration read at " + latencyMillis
                        + " ms moved neither gtid.gate counter");
            }
            return pinned;
        } finally {
            replicationControl.awaitCaughtUp();
        }
    }

    private SeedResult runSeed(long seed, int budget, long latencyBound, Replication replication,
            OregonPrimary oregonPrimary, OregonContext oregon) throws Exception {
        Plan plan = OperationGenerator.plan(seed, budget, latencyBound);
        User author = TestDataFactory.createTestUser(userRepository, "consistency-seed-" + seed + "@example.com");
        Cookie accessToken = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, author));
        Path history = historyFile(seed);
        Duration phaseC;
        try (HistoryRecorder recorder = new HistoryRecorder(Files.newBufferedWriter(history), System::nanoTime)) {
            PlannerPool pool = new PlannerPool();
            List<CookieJar> jars = IntStream.range(0, SESSIONS.size())
                    .mapToObj(index -> index < AUTHOR_DEVICES
                            ? new CookieJar(accessToken, AuthCookies.freshDeviceId())
                            : new CookieJar(AuthCookies.freshDeviceId()))
                    .toList();
            List<PlannerSession> sessions = PlannerSession.forPlan(plan, jars, mockMvc, oregon.mockMvc(),
                    objectMapper, recorder, pool);
            for (int planner = 0; planner < SETUP_PLANNERS; planner++) {
                sessions.get(CREATOR).perform(new PlannedOperation.Create());
            }
            if (pool.size() != SETUP_PLANNERS) {
                throw new IllegalStateException("setup created " + pool.size() + " of " + SETUP_PLANNERS
                        + " planners, history " + history.toAbsolutePath());
            }
            for (int device = 0; device < AUTHOR_DEVICES; device++) {
                sessions.get(device).perform(new PlannedOperation.ListAll());
            }
            phaseC = PhaseRunner.runPlan(plan, sessions, replication, oregonPrimary);
        }
        History recorded = History.read(history);
        Verdict verdict = ConsistencyChecker.check(recorded);
        if (!verdict.violations().isEmpty()) {
            HistoryWriter.write(Shrinker.shrink(recorded, verdict.violations().get(0)), minimalHistoryFile(seed));
        }
        return new SeedResult(seed, plan, history, SeedGuard.failures(recorded.operations()), verdict, phaseC);
    }

    private MeterRegistry registry(CoverageTarget target, OregonContext oregon) {
        return target.region() == Region.SEOUL ? meterRegistry : oregon.meterRegistry();
    }

    private static double count(MeterRegistry registry, CoverageTarget target) {
        Search search = registry.find(target.meter()).tags(target.tags());
        return switch (target.instrument()) {
            case COUNTER -> search.counters().stream().mapToDouble(Counter::count).sum();
            case TIMER -> search.timers().stream().mapToDouble(Timer::count).sum();
        };
    }

    private static Map<String, Region> homes(Plan plan) {
        Map<String, Region> homes = new LinkedHashMap<>();
        for (int index = 0; index < SESSIONS.size(); index++) {
            homes.put(SESSIONS.get(index), plan.homes().get(index));
        }
        return homes;
    }

    private static Map<String, Long> anomalyCounts(Verdict verdict) {
        return verdict.anomalies().stream()
                .collect(Collectors.groupingBy(Anomaly::kind, TreeMap::new, Collectors.counting()));
    }

    private record SeedResult(long seed, Plan plan, Path history, List<String> guardFailures, Verdict verdict,
            Duration phaseC) {
    }

    private static final class HarnessOregonPrimary implements OregonPrimary {

        private final ToxiproxyControl toxiproxy;
        private final MeterRegistry registry;

        HarnessOregonPrimary(ToxiproxyControl toxiproxy, MeterRegistry registry) {
            this.toxiproxy = toxiproxy;
            this.registry = registry;
        }

        @Override
        public void cut() {
            try {
                toxiproxy.cutOregonPrimary();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            await("the Oregon breaker reads open", () -> breakerGauge(CircuitBreaker.State.OPEN) == 1.0);
        }

        @Override
        public void restore() {
            try {
                toxiproxy.restoreOregonPrimary();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            await("the Oregon breaker reads closed", () -> breakerGauge(CircuitBreaker.State.CLOSED) == 1.0);
            await("the Oregon primary pool holds an idle connection", () -> primaryPoolIdle() >= 1.0);
        }

        private void await(String condition, BooleanSupplier reached) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OREGON_DEADLINE_MS);
            while (!reached.getAsBoolean()) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("not within " + OREGON_DEADLINE_MS + " ms: " + condition);
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(OREGON_POLL_INTERVAL_MS));
            }
        }

        private double breakerGauge(CircuitBreaker.State state) {
            Gauge gauge = registry.find(BREAKER_STATE_GAUGE)
                    .tag("name", ReadSourcesConfig.breakerName(ReadSourceProperties.PRIMARY))
                    .tag("state", state.name().toLowerCase(Locale.ROOT)).gauge();
            return gauge == null ? Double.NaN : gauge.value();
        }

        private double primaryPoolIdle() {
            Gauge gauge = registry.find(POOL_IDLE_GAUGE).tag("pool", PoolLedger.PRIMARY_POOL_NAME).gauge();
            return gauge == null ? Double.NaN : gauge.value();
        }
    }

    private static final class HarnessReplication implements Replication {

        private final ReplicationControl control;
        private final ToxiproxyControl toxiproxy;

        HarnessReplication(ReplicationControl control, ToxiproxyControl toxiproxy) {
            this.control = control;
            this.toxiproxy = toxiproxy;
        }

        @Override
        public void stopReplica() {
            control.stopReplica();
        }

        @Override
        public void startReplica() {
            control.startReplica();
        }

        @Override
        public void awaitCaughtUp() {
            control.awaitCaughtUp();
        }

        @Override
        public void applyLatency(long millis) {
            try {
                toxiproxy.applyReplicationLatency(millis);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void removeLatency() {
            try {
                toxiproxy.removeReplicationLatency();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
