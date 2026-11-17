package org.danteplanner.backend.shared.redis;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
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

    private static final Duration CONNECTION_TIMEOUT = STARTUP_PROBE_TIMEOUT;
    private static final String PONG = "PONG";
    private static final CompletableFuture<Void> NO_CONNECTION_OF_ITS_OWN = CompletableFuture.completedFuture(null);

    private final RedisClient client;
    private final List<RoleProbe> probes;

    public RedisRoleProbe(ClientResources clientResources, RedisConnectionConfig connections, RedisRoleHealth health,
            List<RoleConnector> connectors) {
        RedisConnectionConfig.Endpoint auth = RedisRole.AUTH.endpointIn(connections);
        this.client = BoundedRedisConnections.redisClient(clientResources, auth.getHost(), auth.getPort());
        client.setOptions(ClientOptions.builder()
                .autoReconnect(false)
                .socketOptions(SocketOptions.builder().connectTimeout(CONNECTION_TIMEOUT).build())
                .build());
        Map<RedisRole, RoleConnector> connectorByRole = connectors.stream()
                .collect(Collectors.toMap(RoleConnector::role, Function.identity()));
        this.probes = Arrays.stream(RedisRole.values())
                .map(role -> new RoleProbe(role, heldConnectionTo(probeUri(role.endpointIn(connections))),
                        health.breaker(role), connectionOf(connectorByRole, role)))
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
        probes.forEach(probe -> probe.held().close());
        client.shutdown();
    }

    private void probeEveryRole(ProbePhase phase) {
        long deadline = System.nanoTime() + phase.timeout().toNanos();
        List<CompletableFuture<String>> pongs = probes.stream().map(probe -> probe.held().ping()).toList();
        List<CompletableFuture<Void>> connections = probes.stream().map(probe -> probe.connection().get()).toList();
        for (int i = 0; i < probes.size(); i++) {
            RoleProbe probe = probes.get(i);
            boolean ponged = completesBy(pongs.get(i), deadline, PONG::equals);
            probe.held().settle(ponged);
            boolean answered = ponged && completesBy(connections.get(i), deadline, connected -> true);
            probe.apply(phase.decide(probe.streak(), answered));
        }
    }

    private HeldConnection heldConnectionTo(RedisURI uri) {
        return new HeldConnection(() -> client.connectAsync(StringCodec.UTF8, uri).toCompletableFuture());
    }

    private static Supplier<CompletableFuture<Void>> connectionOf(
            Map<RedisRole, RoleConnector> connectorByRole, RedisRole role) {
        RoleConnector connector = connectorByRole.get(role);
        return connector == null ? () -> NO_CONNECTION_OF_ITS_OWN : connector::connect;
    }

    private static <T> boolean completesBy(CompletableFuture<T> future, long deadline, Predicate<T> accepted) {
        try {
            return accepted.test(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
        } catch (ExecutionException | TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static RedisURI probeUri(RedisConnectionConfig.Endpoint endpoint) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(endpoint.getHost())
                .withPort(endpoint.getPort())
                .withTimeout(CONNECTION_TIMEOUT);
        if (StringUtils.hasText(endpoint.getPassword())) {
            uri.withPassword(endpoint.getPassword().toCharArray());
        }
        return uri.build();
    }

    private record RoleProbe(
            RedisRole role,
            HeldConnection held,
            CircuitBreaker breaker,
            Supplier<CompletableFuture<Void>> connection,
            ProbeStreak streak) {

        private RoleProbe(RedisRole role, HeldConnection held, CircuitBreaker breaker,
                Supplier<CompletableFuture<Void>> connection) {
            this(role, held, breaker, connection, new ProbeStreak(FAILURE_THRESHOLD));
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
