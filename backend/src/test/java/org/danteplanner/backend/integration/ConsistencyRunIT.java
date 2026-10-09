package org.danteplanner.backend.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.consistency.CookieJar;
import org.danteplanner.backend.integration.consistency.HistoryRecorder;
import org.danteplanner.backend.integration.consistency.OperationGenerator;
import org.danteplanner.backend.integration.consistency.PhaseRunner;
import org.danteplanner.backend.integration.consistency.Plan;
import org.danteplanner.backend.integration.consistency.PlannedOperation;
import org.danteplanner.backend.integration.consistency.PlannerSession;
import org.danteplanner.backend.integration.consistency.Replication;
import org.danteplanner.backend.integration.consistency.SeedLoop;
import org.danteplanner.backend.integration.consistency.Slot;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.consistency.Anomaly;
import org.danteplanner.consistency.Applied;
import org.danteplanner.consistency.ConsistencyChecker;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_OPS_PER_SEED;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_SEED_COUNT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_SEED_FROM;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.DEFAULT_STOP_AT_FIRST_VIOLATION;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.HISTORY_DIRECTORY;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.OPS_PER_SEED;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SEED_COUNT;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SEED_FROM;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.STOP_AT_FIRST_VIOLATION;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.historyFile;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.sessionName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ConsistencyRunIT extends ReplicaRoutingHarnessSupport {

    private static final int NO_RESPONSE = 0;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void plannerHistories_WhenSessionsRunUnderReplicationFaults_KeepTheContract() throws Exception {
        long seedFrom = Long.getLong(SEED_FROM, DEFAULT_SEED_FROM);
        int seedCount = Integer.getInteger(SEED_COUNT, DEFAULT_SEED_COUNT);
        int budget = Integer.getInteger(OPS_PER_SEED, DEFAULT_OPS_PER_SEED);
        boolean stopAtFirstViolation = Boolean.parseBoolean(
                System.getProperty(STOP_AT_FIRST_VIOLATION, String.valueOf(DEFAULT_STOP_AT_FIRST_VIOLATION)));
        List<Long> seeds = LongStream.range(seedFrom, seedFrom + seedCount).boxed().toList();
        Replication replication = new HarnessReplication(replicationControl);
        Files.createDirectories(HISTORY_DIRECTORY);

        List<SeedResult> results = SeedLoop.run(seeds, replication,
                seed -> runSeed(seed, budget, replication),
                result -> stopAtFirstViolation && !result.verdict().violations().isEmpty());

        results.forEach(result -> System.out.println("consistency seed " + result.seed() + ": "
                + result.verdict().violations().size() + " violations, anomalies "
                + anomalyCounts(result.verdict()) + ", faults A " + result.plan().phaseA().fault()
                + " B " + result.plan().phaseB().fault() + ", history " + result.history().toAbsolutePath()));
        List<String> violations = results.stream()
                .flatMap(result -> result.verdict().violations().stream()
                        .map(violation -> "seed " + result.seed() + " (" + result.history().toAbsolutePath()
                                + "): " + violation))
                .toList();
        List<String> uncheckedSeeds = results.stream()
                .flatMap(result -> guardFailures(result.operations()).stream()
                        .map(failure -> "seed " + result.seed() + " (" + result.history().toAbsolutePath()
                                + "): " + failure))
                .toList();
        assertThat(Stream.concat(uncheckedSeeds.stream(), violations.stream()).toList())
                .as("seeds whose history cannot show a violation, then violations of the ADR 008 contract, "
                        + "by seed and history file")
                .isEmpty();
    }

    private SeedResult runSeed(long seed, int budget, Replication replication) throws Exception {
        Plan plan = OperationGenerator.plan(seed, budget);
        User author = TestDataFactory.createTestUser(userRepository, "consistency-seed-" + seed + "@example.com");
        Cookie accessToken = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, author));
        Map<Slot, UUID> keys = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            keys.put(slot, UUID.randomUUID());
        }
        Path history = historyFile(seed);
        try (HistoryRecorder recorder = new HistoryRecorder(Files.newBufferedWriter(history), System::nanoTime)) {
            List<PlannerSession> sessions = IntStream.range(0, SESSIONS)
                    .mapToObj(index -> new PlannerSession(sessionName(index),
                            new CookieJar(accessToken, AuthCookies.freshDeviceId()),
                            mockMvc, objectMapper, recorder, keys))
                    .toList();
            for (Slot slot : Slot.values()) {
                sessions.get(0).perform(new PlannedOperation.Save(slot));
            }
            PhaseRunner.run(plan.phaseA(), sessions, replication);
            PhaseRunner.run(plan.phaseB(), sessions, replication);
        }
        History recorded = History.read(history);
        return new SeedResult(seed, plan, history, recorded.operations(), ConsistencyChecker.check(recorded));
    }

    private static Map<String, Long> anomalyCounts(Verdict verdict) {
        return verdict.anomalies().stream()
                .collect(Collectors.groupingBy(Anomaly::kind, TreeMap::new, Collectors.counting()));
    }

    private static List<String> guardFailures(List<Operation> operations) {
        List<String> failures = new ArrayList<>();
        long unknown = operations.stream().filter(ConsistencyRunIT::unknown).count();
        if (unknown > 0) {
            failures.add(unknown + " operations have an unknown outcome, and phase 1 injects no client-side fault");
        }
        String author = sessionName(0);
        boolean readsAfterOwnSave = operations.stream()
                .filter(Read.class::isInstance)
                .map(Read.class::cast)
                .filter(read -> read.session().equals(author))
                .filter(read -> read.result() instanceof Found || read.result() instanceof NotFound)
                .anyMatch(read -> operations.stream()
                        .filter(Save.class::isInstance)
                        .map(Save.class::cast)
                        .anyMatch(save -> save.session().equals(author) && save.key().equals(read.key())
                                && save.result() instanceof Applied && save.completedAt() < read.invokedAt()));
        if (!readsAfterOwnSave) {
            failures.add(author + " has no Found or NotFound read of a key after its own applied save of it");
        }
        return failures;
    }

    private static boolean unknown(Operation operation) {
        return switch (operation) {
            case Save save -> save.result() instanceof Unknown;
            case Delete delete -> delete.result() instanceof Unknown;
            case Read read -> read.result() instanceof ReadFailed failed && failed.status() == NO_RESPONSE;
            case ListRead list -> false;
        };
    }

    private record SeedResult(long seed, Plan plan, Path history, List<Operation> operations, Verdict verdict) {
    }

    private static final class HarnessReplication implements Replication {

        private final ReplicationControl control;

        HarnessReplication(ReplicationControl control) {
            this.control = control;
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
    }
}
