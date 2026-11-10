package org.danteplanner.backend.shared.redis;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;

@Slf4j
public class RedisRoleProbe implements AutoCloseable {

    public static final String PROBE_INTERVAL_TEXT = "PT1S";
    public static final Duration PROBE_INTERVAL = Duration.parse(PROBE_INTERVAL_TEXT);
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);
    public static final Duration STARTUP_PROBE_TIMEOUT = BoundedRedisConnections.COMMAND_TIMEOUT;
    public static final int FAILURE_THRESHOLD = 2;

    private static final String PONG = "PONG";

    private final RedisClient client;
    private final List<RoleProbe> probes;

    public RedisRoleProbe(ClientResources clientResources, RedisConnectionConfig connections, RedisRoleHealth health) {
        RedisConnectionConfig.Endpoint auth = RedisRole.AUTH.endpointIn(connections);
        this.client = BoundedRedisConnections.redisClient(clientResources, auth.getHost(), auth.getPort());
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(STARTUP_PROBE_TIMEOUT).build())
                .build());
        this.probes = Arrays.stream(RedisRole.values())
                .map(role -> new RoleProbe(role, probeUris(role.endpointIn(connections)), health.breaker(role)))
                .toList();
    }

    @PostConstruct
    public void probeAtStartup() {
        probeEveryRole(ProbePhase.STARTUP);
    }

    @Scheduled(fixedDelayString = PROBE_INTERVAL_TEXT, scheduler = RedisRoleConfig.PROBE_SCHEDULER)
    public void probe() {
        probeEveryRole(ProbePhase.RUNTIME);
    }

    @Override
    public void close() {
        client.shutdown();
    }

    private void probeEveryRole(ProbePhase phase) {
        long deadline = System.nanoTime() + phase.timeout().toNanos();
        List<Ping> pings = probes.stream().map(probe -> ping(probe.uris().get(phase))).toList();
        try {
            for (int i = 0; i < probes.size(); i++) {
                RoleProbe probe = probes.get(i);
                probe.apply(phase.decide(probe.streak(), pings.get(i).answeredBy(deadline)));
            }
        } finally {
            pings.forEach(Ping::close);
        }
    }

    private Ping ping(RedisURI uri) {
        ConnectionFuture<StatefulRedisConnection<String, String>> connecting =
                client.connectAsync(StringCodec.UTF8, uri);
        CompletableFuture<String> pong = connecting.toCompletableFuture()
                .thenCompose(connection -> connection.async().ping().toCompletableFuture());
        return new Ping(connecting, pong);
    }

    private static Map<ProbePhase, RedisURI> probeUris(RedisConnectionConfig.Endpoint endpoint) {
        Map<ProbePhase, RedisURI> uris = new EnumMap<>(ProbePhase.class);
        for (ProbePhase phase : ProbePhase.values()) {
            uris.put(phase, probeUri(endpoint, phase.timeout()));
        }
        return uris;
    }

    private static RedisURI probeUri(RedisConnectionConfig.Endpoint endpoint, Duration timeout) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(endpoint.getHost())
                .withPort(endpoint.getPort())
                .withTimeout(timeout);
        if (StringUtils.hasText(endpoint.getPassword())) {
            uri.withPassword(endpoint.getPassword().toCharArray());
        }
        return uri.build();
    }

    private record Ping(
            ConnectionFuture<StatefulRedisConnection<String, String>> connecting, CompletableFuture<String> pong) {

        private boolean answeredBy(long deadline) {
            try {
                return PONG.equals(pong.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            } catch (ExecutionException | TimeoutException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private void close() {
            var unused = connecting.thenCompose(StatefulRedisConnection::closeAsync);
        }
    }

    private record RoleProbe(
            RedisRole role, Map<ProbePhase, RedisURI> uris, CircuitBreaker breaker, ProbeStreak streak) {

        private RoleProbe(RedisRole role, Map<ProbePhase, RedisURI> uris, CircuitBreaker breaker) {
            this(role, uris, breaker, new ProbeStreak(FAILURE_THRESHOLD));
        }

        private void apply(ProbeStreak.Transition transition) {
            if (transition == ProbeStreak.Transition.OPEN) {
                breaker.transitionToOpenState();
                log.info("Redis role {} did not answer its probe; breaker {} opened", role.label(), breaker.getName());
            } else if (transition == ProbeStreak.Transition.CLOSE) {
                breaker.transitionToClosedState();
                log.info("Redis role {} answered its probe; breaker {} closed", role.label(), breaker.getName());
            }
        }
    }
}
