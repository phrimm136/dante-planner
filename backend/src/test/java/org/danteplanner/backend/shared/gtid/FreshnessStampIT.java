package org.danteplanner.backend.shared.gtid;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.MarkerStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("containerized")
class FreshnessStampIT {

    private static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");
    private static final String REFUSED_HOST = "127.0.0.1";
    private static final int REFUSED_PORT = 1;
    private static final String GTID_HEADER = "X-Test-Gtid";
    private static final String PLANNER_HEADER = "X-Test-Planner";
    private static final String CATALOG_HEADER = "X-Test-Catalog";
    private static final String NONE = "";

    private static LettuceConnectionFactory liveFactory;
    private static StringRedisTemplate live;
    private static LettuceConnectionFactory refusedFactory;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final GtidWriteCapture writeCapture = new GtidWriteCapture(meters);
    private MarkerStore markerStore;
    private String serverA;
    private String serverB;

    @RestController
    @RequestMapping("/api/stamp")
    public static class StampingController {

        private final GtidWriteCapture writeCapture;

        StampingController(GtidWriteCapture writeCapture) {
            this.writeCapture = writeCapture;
        }

        @PostMapping
        public ResponseEntity<Map<String, Boolean>> writeWithBody(
                @RequestHeader(value = GTID_HEADER, defaultValue = NONE) String gtid,
                @RequestHeader(value = PLANNER_HEADER, defaultValue = NONE) String plannerId,
                @RequestHeader(value = CATALOG_HEADER, defaultValue = "false") boolean catalog) {
            act(gtid, plannerId, catalog);
            return ResponseEntity.ok(Map.of("written", true));
        }

        @DeleteMapping
        public ResponseEntity<Void> writeWithoutBody(
                @RequestHeader(value = GTID_HEADER, defaultValue = NONE) String gtid,
                @RequestHeader(value = PLANNER_HEADER, defaultValue = NONE) String plannerId,
                @RequestHeader(value = CATALOG_HEADER, defaultValue = "false") boolean catalog) {
            act(gtid, plannerId, catalog);
            return ResponseEntity.noContent().build();
        }

        @GetMapping
        public ResponseEntity<Map<String, Boolean>> read(
                @RequestHeader(value = PLANNER_HEADER, defaultValue = NONE) String plannerId,
                @RequestHeader(value = CATALOG_HEADER, defaultValue = "false") boolean catalog) {
            act(NONE, plannerId, catalog);
            return ResponseEntity.ok(Map.of("written", false));
        }

        private void act(String gtid, String plannerId, boolean catalog) {
            if (!plannerId.isEmpty()) {
                FreshnessDeclarations.declare(new FreshnessScope.Entity(UUID.fromString(plannerId)));
            }
            if (catalog) {
                FreshnessDeclarations.declare(new FreshnessScope.PublicCatalog());
            }
            if (!gtid.isEmpty()) {
                writeCapture.recordCommit(gtid, true);
            }
        }
    }

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        liveFactory = factory(REDIS.getRedisHost(), REDIS.getRedisPort());
        live = new StringRedisTemplate(liveFactory);
        live.afterPropertiesSet();
        refusedFactory = factory(REFUSED_HOST, REFUSED_PORT);
    }

    @AfterAll
    static void stopRedis() {
        liveFactory.destroy();
        refusedFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        serverA = UUID.randomUUID().toString();
        serverB = UUID.randomUUID().toString();
        markerStore = storeOver(liveFactory);
    }

    @AfterEach
    void tearDown() {
        markerStore.close();
    }

    private static LettuceConnectionFactory factory(String host, int port) {
        LettuceConnectionFactory factory =
                BoundedRedisConnections.connectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        return factory;
    }

    private MarkerStore storeOver(LettuceConnectionFactory writeStore) {
        return new MarkerStore(writeStore, writeStore.getRequiredNativeClient().getResources(), List.of(),
                new MarkerStore.Settings(0, Duration.ofSeconds(1), Duration.ofHours(1)), meters);
    }

    private MockMvc mvc() {
        ReadSourceSelector primaryOnly = new ReadSourceSelector(List.of(new ReadSourceSelector.Source(
                "primary", true, null, CircuitBreaker.ofDefaults("primary-" + UUID.randomUUID()))));
        GtidCookieFilter filter = new GtidCookieFilter(new GtidReadGate(primaryOnly, meters), writeCapture,
                markerStore, new ProblemWriter(new ObjectMapper()));
        return MockMvcBuilders.standaloneSetup(new StampingController(writeCapture))
                .setControllerAdvice(new GtidCookieResponseAdvice(writeCapture, markerStore))
                .addFilters(filter)
                .build();
    }

    private Map<Object, Object> marker(FreshnessScope scope) {
        return live.opsForHash().entries(scope.key());
    }

    private String gtidSet() {
        return serverA + ":1-5:7-9," + serverB + ":3-4";
    }

    private static void assertCookieCarries(MvcResult result, String gtid) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .contains(GtidCookie.NAME + "=" + GtidCookie.of(GtidWriteCapture.unionGtidSets(
                                Set.of(gtid))).getValue()));
    }

    @Test
    void bodiedWrite_WhenItCommitsAndDeclaresAPlannerAndTheCatalog_RaisesBothToItsGtidAndMintsTheCookie()
            throws Exception {
        UUID plannerId = UUID.randomUUID();

        MvcResult result = mvc().perform(post("/api/stamp")
                        .header(GTID_HEADER, gtidSet())
                        .header(PLANNER_HEADER, plannerId.toString())
                        .header(CATALOG_HEADER, true))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(marker(new FreshnessScope.Entity(plannerId)))
                .containsOnly(Map.entry(serverA, "9"), Map.entry(serverB, "4"));
        assertThat(marker(new FreshnessScope.PublicCatalog()))
                .contains(Map.entry(serverA, "9"), Map.entry(serverB, "4"));
        assertCookieCarries(result, gtidSet());
    }

    @Test
    void bodilessWrite_WhenItCommitsAndDeclaresAPlannerAndTheCatalog_RaisesBothToItsGtidAndMintsTheCookie()
            throws Exception {
        UUID plannerId = UUID.randomUUID();

        MvcResult result = mvc().perform(delete("/api/stamp")
                        .header(GTID_HEADER, gtidSet())
                        .header(PLANNER_HEADER, plannerId.toString())
                        .header(CATALOG_HEADER, true))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(marker(new FreshnessScope.Entity(plannerId)))
                .containsOnly(Map.entry(serverA, "9"), Map.entry(serverB, "4"));
        assertThat(marker(new FreshnessScope.PublicCatalog()))
                .contains(Map.entry(serverA, "9"), Map.entry(serverB, "4"));
        assertCookieCarries(result, gtidSet());
    }

    @Test
    void write_WhenItDeclaresButCommitsNothing_RaisesNoMarker() throws Exception {
        UUID plannerId = UUID.randomUUID();

        mvc().perform(post("/api/stamp")
                        .header(PLANNER_HEADER, plannerId.toString()))
                .andExpect(status().isOk());

        assertThat(marker(new FreshnessScope.Entity(plannerId))).isEmpty();
    }

    @Test
    void readGet_WhenItDeclaresAndCommitsNothing_RaisesNoMarker() throws Exception {
        UUID plannerId = UUID.randomUUID();

        mvc().perform(get("/api/stamp")
                        .header(PLANNER_HEADER, plannerId.toString()))
                .andExpect(status().isOk());

        assertThat(marker(new FreshnessScope.Entity(plannerId))).isEmpty();
    }

    @Test
    void write_WhenItCommitsButDeclaresNothing_RaisesNoMarkerAndStillMintsTheCookie() throws Exception {
        MvcResult result = mvc().perform(post("/api/stamp")
                        .header(GTID_HEADER, gtidSet()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(marker(new FreshnessScope.PublicCatalog())).doesNotContainKeys(serverA, serverB);
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
        assertCookieCarries(result, gtidSet());
    }

    @Test
    void write_WhenTheMarkerStoreIsDown_StillAnswersAndCountsTheFailure() throws Exception {
        markerStore.close();
        markerStore = storeOver(refusedFactory);

        MvcResult result = mvc().perform(post("/api/stamp")
                        .header(GTID_HEADER, gtidSet())
                        .header(PLANNER_HEADER, UUID.randomUUID().toString())
                        .header(CATALOG_HEADER, true))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isEqualTo(1.0);
        assertCookieCarries(result, gtidSet());
    }
}
