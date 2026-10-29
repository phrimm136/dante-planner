package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.StaticCredentialsProvider;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.time.DurationMin;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;

@Slf4j
public class MarkerStore implements AutoCloseable {

    public static final String RAISE_FAILURES = "freshness.marker.raise.failures";
    public static final String WAIT_TIMEOUTS = "freshness.marker.wait.timeouts";
    public static final String PROBE_SCHEDULER = "markerSourceProbeScheduler";
    public static final String BREAKER_NAME_PREFIX = "marker-source-";
    public static final String LOCAL_SOURCE = "auth-local";
    public static final String AUTH_SOURCE = "auth";

    static final String PROBE_INTERVAL_TEXT = "PT1S";
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);
    static final Duration RAISE_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    static final Duration RAISE_MARGIN = Duration.ofMillis(500);
    static final int PROBE_FAILURE_THRESHOLD = 2;

    private static final String SEQUENCE_RANGE_START = ":1-";
    private static final String SOURCE_SEPARATOR = ",";
    private static final String INTERVAL_SEPARATOR = ":";
    private static final char RANGE_SEPARATOR = '-';

    public sealed interface MarkerView {

        record Absent() implements MarkerView {
        }

        record At(String gtidSet) implements MarkerView {
        }

        record Unknown() implements MarkerView {
        }
    }

    public record Source(
            String name,
            StringRedisTemplate store,
            RedisConnectionConfig.Endpoint endpoint,
            CircuitBreaker breaker) {
    }

    @ConfigurationProperties(prefix = "freshness.marker")
    @Validated
    public record Settings(
            @Min(0) int waitReplicas,
            @NotNull @DurationMin(millis = 1) Duration waitBound,
            @NotNull @DurationMin(millis = 1) Duration ttl) {

        @AssertTrue(message = "freshness.marker.wait-bound must stay under the Redis command timeout, "
                + "or WAIT fails as a raise failure instead of answering")
        public boolean isWaitBoundUnderTheCommandTimeout() {
            return waitBound == null || waitBound.compareTo(BoundedRedisConnections.COMMAND_TIMEOUT) < 0;
        }
    }

    private final List<Source> readSources;
    private final Settings settings;
    private final MarkerSourceProbe sourceProbe;
    private final RaiseBatcher raiseBatcher;

    public MarkerStore(
            LettuceConnectionFactory writeStore,
            ClientResources clientResources,
            List<Source> readSources,
            Settings settings,
            MeterRegistry meterRegistry) {
        this.readSources = List.copyOf(readSources);
        this.settings = settings;
        this.sourceProbe = new MarkerSourceProbe(
                clientResources, this.readSources, PROBE_TIMEOUT, PROBE_FAILURE_THRESHOLD);
        this.raiseBatcher = new RaiseBatcher(() -> raiseEndpoint(writeStore, clientResources),
                settings.waitReplicas(), settings.waitBound(), RAISE_MARGIN, meterRegistry.counter(RAISE_FAILURES),
                meterRegistry.counter(WAIT_TIMEOUTS), System::nanoTime);
    }

    public static List<Source> sourceOrder(boolean readsPrimaryFirst, Source local, Source auth) {
        return readsPrimaryFirst ? List.of(auth) : List.of(local, auth);
    }

    public void raise(Collection<FreshnessScope> scopes, String gtidSet) {
        if (scopes.isEmpty()) {
            return;
        }
        String[] keys = scopes.stream().map(FreshnessScope::key).distinct().toArray(String[]::new);
        raiseBatcher.raise(keys, raiseArguments(settings.ttl(), gtidSet));
    }

    public Map<FreshnessScope, MarkerView> read(Collection<FreshnessScope> scopes) {
        for (Source source : readSources) {
            if (source.breaker().getState() != CircuitBreaker.State.CLOSED) {
                continue;
            }
            Optional<Map<FreshnessScope, MarkerView>> views = readFrom(source, scopes);
            if (views.isPresent()) {
                return views.get();
            }
        }
        Map<FreshnessScope, MarkerView> unknown = new LinkedHashMap<>();
        scopes.forEach(scope -> unknown.put(scope, new MarkerView.Unknown()));
        return unknown;
    }

    @Scheduled(fixedDelayString = PROBE_INTERVAL_TEXT, scheduler = PROBE_SCHEDULER)
    public void probe() {
        sourceProbe.probe();
    }

    @Override
    public void close() {
        sourceProbe.close();
        raiseBatcher.close();
    }

    private static RaiseBatcher.Endpoint raiseEndpoint(
            LettuceConnectionFactory writeStore, ClientResources clientResources) {
        RedisStandaloneConfiguration configuration = writeStore.getStandaloneConfiguration();
        RedisURI uri = BoundedRedisConnections.redisUri(configuration.getHostName(), configuration.getPort());
        uri.setDatabase(configuration.getDatabase());
        configuration.getPassword().toOptional()
                .ifPresent(password -> uri.setCredentialsProvider(new StaticCredentialsProvider(null, password)));
        RedisClient client = BoundedRedisConnections.redisClient(
                clientResources, configuration.getHostName(), configuration.getPort());
        client.setOptions(ClientOptions.builder()
                .autoReconnect(false)
                .socketOptions(SocketOptions.builder().connectTimeout(RAISE_CONNECT_TIMEOUT).build())
                .build());
        return new RaiseBatcher.Endpoint(client, uri);
    }

    private Optional<Map<FreshnessScope, MarkerView>> readFrom(Source source, Collection<FreshnessScope> scopes) {
        long start = System.nanoTime();
        try {
            Map<FreshnessScope, MarkerView> views = new LinkedHashMap<>();
            for (FreshnessScope scope : scopes) {
                views.put(scope, viewOf(source.store().<String, String>opsForHash().entries(scope.key())));
            }
            source.breaker().onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return Optional.of(views);
        } catch (DataAccessException e) {
            source.breaker().onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            log.warn("Freshness markers not read from {}: trying the next source", source.name(), e);
            return Optional.empty();
        }
    }

    static MarkerView viewOf(Map<String, String> marker) {
        if (marker.isEmpty()) {
            return new MarkerView.Absent();
        }
        return new MarkerView.At(new TreeMap<>(marker).entrySet().stream()
                .map(entry -> entry.getKey() + SEQUENCE_RANGE_START + entry.getValue())
                .collect(Collectors.joining(SOURCE_SEPARATOR)));
    }

    static String[] raiseArguments(Duration ttl, String gtidSet) {
        List<String> arguments = new ArrayList<>();
        arguments.add(String.valueOf(ttl.toMillis()));
        highestSequences(gtidSet).forEach((uuid, sequence) -> {
            arguments.add(uuid);
            arguments.add(String.valueOf(sequence));
        });
        return arguments.toArray(String[]::new);
    }

    static Map<String, Long> highestSequences(String gtidSet) {
        Map<String, Long> highest = new TreeMap<>();
        for (String sourceSet : gtidSet.split(SOURCE_SEPARATOR)) {
            String[] parts = sourceSet.trim().split(INTERVAL_SEPARATOR);
            String uuid = parts[0].toLowerCase(Locale.ROOT);
            for (int i = 1; i < parts.length; i++) {
                highest.merge(uuid, intervalEnd(parts[i]), Math::max);
            }
        }
        return highest;
    }

    private static long intervalEnd(String interval) {
        int dash = interval.indexOf(RANGE_SEPARATOR);
        return Long.parseLong(dash < 0 ? interval : interval.substring(dash + 1));
    }
}
