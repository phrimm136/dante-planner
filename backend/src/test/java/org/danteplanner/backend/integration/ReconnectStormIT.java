package org.danteplanner.backend.integration;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.TimeoutHierarchy;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.RealClockBounds;
import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReconnectStormIT {

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_INTERNAL_PORT = 6379;
    private static final int MYSQL_INTERNAL_PORT = 3306;
    private static final String PRIMARY_ALIAS = "storm-mysql-primary";

    private static final String ROOT_USER = "root";
    private static final String REPL_USER = "repl";
    private static final String REPL_PASSWORD = "repl-pass";
    private static final String REPLICA_SOURCE = "seoul-replica";

    private static final String PRIMARY_POOL_BEAN = "primaryPool";
    private static final String POOL_TAG = "pool";

    private static final int STORM_CLIENTS = 40;
    private static final int SEEDED_COMMENTS = 3;
    private static final Duration REQUEST_LATENCY_BOUND = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = REQUEST_LATENCY_BOUND.plus(RealClockBounds.SCHEDULING_SLACK);
    private static final String PINNED_EVENT = "jdk.VirtualThreadPinned";
    private static final String BLOCKING_OPERATION = "blockingOperation";
    private static final String PARK = "LockSupport.park";

    private static final String COMMENTS = "GET /comments";
    private static final String COMMENT_EVENTS = "SSE /comments/events";
    private static final String USER_EVENTS = "SSE /sse/subscribe";
    private static final String AUTH_ME = "GET /api/auth/me";
    private static final String SETTINGS = "GET /api/user/settings";

    private static final int TRANSPORT_FAILURE = -1;
    private static final ObjectMapper DUMP_MAPPER = new ObjectMapper();

    private static final Network STORM_NETWORK = Network.newNetwork();

    static final MySQLContainer PRIMARY = mysql(1)
            .withNetworkAliases(PRIMARY_ALIAS);

    static final MySQLContainer REPLICA = mysql(2);

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_INTERNAL_PORT);

    static final JdbcTemplate PRIMARY_ADMIN;

    static {
        PRIMARY.start();
        REPLICA.start();
        REDIS.start();
        PRIMARY_ADMIN = new JdbcTemplate(adminDataSource(PRIMARY));
        wireReplication(PRIMARY_ADMIN, new JdbcTemplate(adminDataSource(REPLICA)));
    }

    @LocalServerPort
    private int port;

    @Autowired
    @Qualifier(PRIMARY_POOL_BEAN)
    private HikariDataSource primaryPool;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private RedisRoleHealth roleHealth;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @DynamicPropertySource
    static void harnessProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registry.add("spring.flyway.url", PRIMARY::getJdbcUrl);
        registry.add("spring.flyway.user", PRIMARY::getUsername);
        registry.add("spring.flyway.password", PRIMARY::getPassword);

        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.read-sources[0].name", () -> ReadSourceProperties.PRIMARY);
        registry.add("datasource.read-sources[1].name", () -> REPLICA_SOURCE);
        registry.add("datasource.read-sources[1].url", REPLICA::getJdbcUrl);
        registry.add("datasource.read-sources[1].username", REPLICA::getUsername);
        registry.add("datasource.read-sources[1].password", REPLICA::getPassword);

        for (RedisRole role : RedisRole.values()) {
            registry.add("redis." + role.label() + ".host", REDIS::getHost);
            registry.add("redis." + role.label() + ".port", () -> REDIS.getMappedPort(REDIS_INTERNAL_PORT));
        }
    }

    @AfterAll
    static void stopHarness() {
        REDIS.stop();
        REPLICA.stop();
        PRIMARY.stop();
        STORM_NETWORK.close();
    }

    @BeforeEach
    void requireTheOregonPool() {
        assertThat(primaryPool.getMaximumPoolSize()).isEqualTo(PoolLedger.OREGON_PRIMARY_POOL);
        assertThat(primaryPool.getConnectionTimeout()).isEqualTo(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS);
        Hang.until(() -> List.of(RedisRole.values()).stream().allMatch(roleHealth::isAvailable),
                "the probe closing every Redis role breaker");
    }

    @Test
    void reconnectStorm_WhenEveryClientReconnectsAtOnce_AnswersEveryReadWithoutPinningACarrier() {
        Storm storm = storm();
        String report = storm.report();
        assertThat(storm.calls().stream().filter(call -> !call.isStream()).toList())
                .as("%s", report)
                .allSatisfy(call -> assertThat(call.status()).as(call.endpoint()).isEqualTo(200));
        assertThat(storm.pins().stream().filter(Pin::mayHaveParkedInProjectCode).toList()).as("%s", report).isEmpty();
    }

    private Storm storm() {
        UUID plannerId = seedCommentedPlanner();
        List<Client> clients = clients();
        try (HttpClient warmUp = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            Call warm = await(send(warmUp, COMMENTS + " warm-up", commentsPath(plannerId), clients.get(1),
                    () -> { }), "the warm-up comment read");
            assertThat(warm.status()).as("warm-up read before the storm").isEqualTo(200);
        }

        ThreadDump dump = new ThreadDump();
        List<Stream<String>> openBodies = Collections.synchronizedList(new ArrayList<>());
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        List<Pin> pins = Collections.synchronizedList(new ArrayList<>());
        try (RecordingStream pinned = new RecordingStream()) {
            pinned.enable(PINNED_EVENT).withThreshold(Duration.ZERO).withStackTrace();
            pinned.onEvent(PINNED_EVENT, event -> pins.add(Pin.of(event)));
            pinned.startAsync();
            CompletableFuture<Void> release = new CompletableFuture<>();
            List<CompletableFuture<Call>> requests = new ArrayList<>();
            for (Client client : clients) {
                requests.add(release.thenCompose(ignored -> stream(http, COMMENT_EVENTS,
                        "/api/planner/" + plannerId + "/comments/events", client, openBodies)));
                requests.add(release.thenCompose(ignored -> stream(http, USER_EVENTS,
                        "/api/sse/subscribe", client, openBodies)));
                requests.add(release.thenCompose(ignored ->
                        send(http, COMMENTS, commentsPath(plannerId), client, dump::takeOnce)));
                if (client.member()) {
                    requests.add(release.thenCompose(ignored ->
                            send(http, AUTH_ME, "/api/auth/me", client, dump::takeOnce)));
                    requests.add(release.thenCompose(ignored ->
                            send(http, SETTINGS, "/api/user/settings", client, dump::takeOnce)));
                }
            }
            release.complete(null);
            List<Call> calls = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                calls.add(await(requests.get(i), "storm request #" + i));
            }
            Hang.until(() -> primaryPool.getHikariPoolMXBean().getActiveConnections() == 0
                            && primaryPool.getHikariPoolMXBean().getThreadsAwaitingConnection() == 0,
                    "the primary pool draining");
            pinned.stop();
            return new Storm(calls, poolLine(), dump.groups(), List.copyOf(pins));
        } finally {
            synchronized (openBodies) {
                openBodies.forEach(Stream::close);
            }
            http.shutdownNow();
        }
    }

    private String poolLine() {
        Timer usage = meterRegistry.find("hikaricp.connections.usage")
                .tag(POOL_TAG, PoolLedger.PRIMARY_POOL_NAME).timer();
        Timer acquire = meterRegistry.find("hikaricp.connections.acquire")
                .tag(POOL_TAG, PoolLedger.PRIMARY_POOL_NAME).timer();
        Counter timeouts = meterRegistry.find("hikaricp.connections.timeout")
                .tag(POOL_TAG, PoolLedger.PRIMARY_POOL_NAME).counter();
        assertThat(usage).as("the primary pool's usage timer").isNotNull();
        assertThat(acquire).as("the primary pool's acquire timer").isNotNull();
        assertThat(timeouts).as("the primary pool's timeout counter").isNotNull();
        return "\nprimary pool: max usage " + (long) usage.max(TimeUnit.MILLISECONDS) + " ms, max acquire "
                + (long) acquire.max(TimeUnit.MILLISECONDS) + " ms, borrows " + usage.count()
                + ", acquire timeouts " + (long) timeouts.count();
    }

    private List<Client> clients() {
        List<Client> clients = new ArrayList<>();
        for (int i = 0; i < STORM_CLIENTS; i++) {
            String token = null;
            if (i % 2 == 1) {
                User member = TestDataFactory.createTestUser(
                        userRepository, "storm-member-" + UUID.randomUUID() + "@example.com");
                token = TestDataFactory.generateAccessToken(jwtTokenService, member);
            }
            clients.add(new Client(UUID.randomUUID(), token));
        }
        return clients;
    }

    private UUID seedCommentedPlanner() {
        User author = TestDataFactory.createTestUser(
                userRepository, "storm-author-" + UUID.randomUUID() + "@example.com");
        UUID plannerId = TestDataFactory.createTestPlanner(plannerRepository, author, true).id();
        for (int i = 0; i < SEEDED_COMMENTS; i++) {
            StoredComments.save(commentRepository, PRIMARY_ADMIN,
                    new PlannerComment(plannerId, author.getId(), null, 0), "storm comment " + i);
        }
        return plannerId;
    }

    private static String commentsPath(UUID plannerId) {
        return "/api/planner/" + plannerId + "/comments";
    }

    private HttpRequest.Builder request(String path, Client client, Duration timeout) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.COOKIE, client.cookies())
                .timeout(timeout)
                .GET();
    }

    private CompletableFuture<Call> send(HttpClient http, String endpoint, String path, Client client,
            Runnable onFailure) {
        String name = endpoint + (client.member() ? " member" : " anon");
        return http.sendAsync(request(path, client, REQUEST_TIMEOUT).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Call(name, false, response.statusCode(),
                        response.statusCode() == 200 ? "" : response.body()))
                .exceptionally(failure -> new Call(name, false, TRANSPORT_FAILURE, failure.toString()))
                .thenApply(call -> {
                    if (call.status() != 200) {
                        onFailure.run();
                    }
                    return call;
                });
    }

    private CompletableFuture<Call> stream(HttpClient http, String endpoint, String path, Client client,
            List<Stream<String>> openBodies) {
        String name = endpoint + (client.member() ? " member" : " anon");
        HttpRequest request = request(path, client, Hang.GUARD)
                .header(HttpHeaders.ACCEPT, "text/event-stream").build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenApply(response -> {
                    openBodies.add(response.body());
                    return new Call(name, true, response.statusCode(), "");
                })
                .exceptionally(failure -> new Call(name, true, TRANSPORT_FAILURE, failure.toString()));
    }

    private static Call await(CompletableFuture<Call> request, String what) {
        try {
            return Hang.get(request, what);
        } catch (ExecutionException e) {
            throw new IllegalStateException(what + " failed", e.getCause());
        }
    }

    private static MySQLContainer mysql(int serverId) {
        return new MySQLContainer(SharedMySqlContainerSupport.MYSQL_IMAGE)
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=512m"))
                .withNetwork(STORM_NETWORK)
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                        "--server-id=" + serverId,
                        "--log-bin=mysql-bin",
                        "--binlog-format=ROW",
                        "--gtid-mode=ON",
                        "--enforce-gtid-consistency=ON",
                        "--innodb-buffer-pool-size=64M",
                        "--innodb-flush-log-at-trx-commit=0",
                        "--innodb-doublewrite=0",
                        "--sync-binlog=0",
                        "--performance-schema=OFF",
                        "--skip-name-resolve");
    }

    private static void wireReplication(JdbcTemplate primary, JdbcTemplate replica) {
        primary.execute("CREATE USER IF NOT EXISTS '" + REPL_USER + "'@'%' "
                + "IDENTIFIED BY '" + REPL_PASSWORD + "'");
        primary.execute("GRANT REPLICATION SLAVE ON *.* TO '" + REPL_USER + "'@'%'");
        primary.execute("FLUSH PRIVILEGES");

        String primaryGtid = primary.queryForObject("SELECT @@GLOBAL.gtid_executed", String.class);

        replica.execute("STOP REPLICA");
        replica.execute("RESET REPLICA ALL");
        replica.execute("RESET BINARY LOGS AND GTIDS");
        if (primaryGtid != null && !primaryGtid.isBlank()) {
            replica.execute("SET GLOBAL gtid_purged = '" + primaryGtid.replaceAll("\\s+", "") + "'");
        }
        replica.execute("CHANGE REPLICATION SOURCE TO "
                + "SOURCE_HOST='" + PRIMARY_ALIAS + "', "
                + "SOURCE_PORT=" + MYSQL_INTERNAL_PORT + ", "
                + "SOURCE_USER='" + REPL_USER + "', "
                + "SOURCE_PASSWORD='" + REPL_PASSWORD + "', "
                + "SOURCE_SSL=1, "
                + "SOURCE_AUTO_POSITION=1");
        replica.execute("START REPLICA");
    }

    private static DataSource adminDataSource(MySQLContainer container) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName(container.getDriverClassName());
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(ROOT_USER);
        dataSource.setPassword(container.getPassword());
        dataSource.setSuppressClose(true);
        return dataSource;
    }

    private record Client(UUID deviceId, String token) {

        boolean member() {
            return token != null;
        }

        String cookies() {
            return CookieConstants.DEVICE_ID + "=" + deviceId
                    + (token == null ? "" : "; " + CookieConstants.ACCESS_TOKEN + "=" + token);
        }
    }

    private record Call(String endpoint, boolean isStream, int status, String body) {
    }

    private record Pin(String operation, List<String> frames, boolean truncated) {

        static Pin of(RecordedEvent event) {
            return new Pin(event.hasField(BLOCKING_OPERATION) ? event.getString(BLOCKING_OPERATION) : PARK,
                    event.getStackTrace().getFrames().stream().map(Pin::frame).toList(),
                    event.getStackTrace().isTruncated());
        }

        private static String frame(RecordedFrame frame) {
            return frame.getMethod().getType().getName() + "." + frame.getMethod().getName()
                    + ":" + frame.getLineNumber();
        }

        boolean mayHaveParkedInProjectCode() {
            return PARK.equals(operation)
                    && frames.stream().noneMatch(frame -> frame.contains(".<clinit>:"))
                    && (truncated || frames.stream().anyMatch(ThreadDump::isProjectFrame));
        }

        String topProjectFrame() {
            return frames.stream().filter(ThreadDump::isProjectFrame).findFirst()
                    .orElse("[stack truncated before any org.danteplanner frame]");
        }
    }

    private record Storm(List<Call> calls, String poolLine, String dumpGroups, List<Pin> pins) {

        String report() {
            StringBuilder text = new StringBuilder()
                    .append("\n=== reconnect storm: ").append(STORM_CLIENTS).append(" clients (half members), ")
                    .append(calls.size()).append(" requests ===")
                    .append(poolLine)
                    .append("\nper endpoint:");
            calls.stream()
                    .collect(Collectors.groupingBy(Call::endpoint, TreeMap::new,
                            Collectors.groupingBy(Call::status, TreeMap::new, Collectors.counting())))
                    .forEach((endpoint, statuses) -> text.append("\n  ").append(endpoint)
                            .append(": statuses ").append(statuses));
            text.append("\nfailure bodies:");
            calls.stream()
                    .filter(call -> call.status() != 200)
                    .collect(Collectors.groupingBy(call -> call.endpoint() + " " + call.status() + " "
                            + call.body(), TreeMap::new, Collectors.counting()))
                    .forEach((body, count) -> text.append("\n  x").append(count).append(" ").append(body));
            List<Pin> projectPins = pins.stream().filter(Pin::mayHaveParkedInProjectCode).toList();
            Map<String, Long> byOperation = pins.stream()
                    .collect(Collectors.groupingBy(Pin::operation, TreeMap::new, Collectors.counting()));
            text.append("\n").append(PINNED_EVENT).append(": ").append(pins.size()).append(" events by operation ")
                    .append(byOperation).append(", ").append(pins.stream().filter(Pin::truncated).count())
                    .append(" with a truncated stack, ").append(projectPins.size())
                    .append(" parks outside class initialization with an org.danteplanner frame or a truncated stack")
                    .append("\npinned parks by top org.danteplanner frame:");
            projectPins.stream()
                    .collect(Collectors.groupingBy(Pin::topProjectFrame, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(group -> text.append("\n  x").append(group.getValue()).append(" ")
                            .append(group.getKey()));
            projectPins.stream().findFirst().ifPresent(first -> text.append("\nfirst pinned stack:")
                    .append(first.frames().stream().collect(Collectors.joining("\n    ", "\n    ", ""))));
            return text.append("\nthread dump at the first failure:").append(dumpGroups).append('\n').toString();
        }
    }

    private static final class ThreadDump {

        private final AtomicBoolean taken = new AtomicBoolean();
        private final AtomicReference<String> groups = new AtomicReference<>("\n  none taken: nothing failed");

        void takeOnce() {
            if (!taken.compareAndSet(false, true)) {
                return;
            }
            try {
                Path dir = Files.createTempDirectory("reconnect-storm");
                Path file = dir.resolve("threads.json");
                ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                        .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);
                groups.set(grouped(DUMP_MAPPER.readTree(Files.readString(file))));
                Files.delete(file);
                Files.delete(dir);
            } catch (IOException | RuntimeException e) {
                groups.set("\n  the dump failed: " + e);
            }
        }

        String groups() {
            return groups.get();
        }

        private static String grouped(JsonNode root) {
            List<List<String>> stacks = new ArrayList<>();
            for (JsonNode container : root.path("threadDump").path("threadContainers").values()) {
                for (JsonNode thread : container.path("threads").values()) {
                    stacks.add(thread.path("stack").values().stream().map(JsonNode::asString).toList());
                }
            }
            Map<String, Long> groups = stacks.stream()
                    .filter(frames -> frames.stream().anyMatch(ThreadDump::isProjectFrame))
                    .collect(Collectors.groupingBy(ThreadDump::trimmed, LinkedHashMap::new,
                            Collectors.counting()));
            long others = stacks.size() - groups.values().stream().mapToLong(Long::longValue).sum();
            StringBuilder text = new StringBuilder();
            groups.forEach((stack, count) -> text.append("\n  x").append(count).append("\n").append(stack));
            return text.append("\n  x").append(others).append(" threads without an org.danteplanner frame")
                    .toString();
        }

        private static String trimmed(List<String> frames) {
            List<String> kept = new ArrayList<>();
            frames.stream()
                    .filter(frame -> !isJdkFrame(frame) && !isProjectFrame(frame))
                    .findFirst()
                    .ifPresent(frame -> kept.add("[framework] " + frame));
            frames.stream().filter(ThreadDump::isProjectFrame).forEach(kept::add);
            return kept.stream().collect(Collectors.joining("\n    ", "    ", ""));
        }

        private static boolean isProjectFrame(String frame) {
            return frame.contains("org.danteplanner.");
        }

        private static boolean isJdkFrame(String frame) {
            return frame.matches("^(java|javax|jdk|sun)\\..*");
        }
    }
}
