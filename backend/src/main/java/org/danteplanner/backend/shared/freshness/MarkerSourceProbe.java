package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.util.StringUtils;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;

@Slf4j
final class MarkerSourceProbe implements AutoCloseable {

    private static final String PONG = "PONG";

    private final List<SourceProbe> probes;
    private final Optional<RedisClient> client;
    private final int failureThreshold;

    MarkerSourceProbe(
            ClientResources clientResources, List<MarkerStore.Source> sources, Duration timeout, int failureThreshold) {
        this.failureThreshold = failureThreshold;
        this.client = sources.stream().findFirst()
                .map(first -> probeClient(clientResources, first.endpoint(), timeout));
        this.probes = sources.stream().map(source -> new SourceProbe(source, probeUri(source.endpoint(), timeout))).toList();
    }

    void probe() {
        probes.forEach(SourceProbe::probe);
    }

    @Override
    public void close() {
        client.ifPresent(RedisClient::shutdown);
    }

    private static RedisClient probeClient(
            ClientResources clientResources, RedisConnectionConfig.Endpoint endpoint, Duration timeout) {
        RedisClient probeClient = BoundedRedisConnections.redisClient(
                clientResources, endpoint.getHost(), endpoint.getPort());
        probeClient.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                .build());
        return probeClient;
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

    private final class SourceProbe {

        private final MarkerStore.Source source;
        private final RedisURI uri;
        private int consecutiveFailures;

        private SourceProbe(MarkerStore.Source source, RedisURI uri) {
            this.source = source;
            this.uri = uri;
        }

        private void probe() {
            CircuitBreaker breaker = source.breaker();
            if (answers()) {
                consecutiveFailures = 0;
                if (breaker.getState() != CircuitBreaker.State.CLOSED) {
                    breaker.transitionToClosedState();
                    log.info("Marker source {} answered its probe; breaker {} closed", source.name(), breaker.getName());
                }
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold && breaker.getState() != CircuitBreaker.State.OPEN) {
                breaker.transitionToOpenState();
                log.warn("Marker source {} failed {} consecutive probes; breaker {} opened",
                        source.name(), consecutiveFailures, breaker.getName());
            }
        }

        private boolean answers() {
            try (StatefulRedisConnection<String, String> connection =
                    client.orElseThrow().connect(StringCodec.UTF8, uri)) {
                return PONG.equals(connection.sync().ping());
            } catch (RedisException e) {
                return false;
            }
        }
    }
}
