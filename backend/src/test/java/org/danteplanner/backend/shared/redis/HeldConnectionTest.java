package org.danteplanner.backend.shared.redis;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.PongRedis;

import static org.assertj.core.api.Assertions.assertThat;

class HeldConnectionTest {

    private final AtomicInteger connects = new AtomicInteger();
    private final AtomicReference<CompletableFuture<StatefulRedisConnection<String, String>>> nextAttempt =
            new AtomicReference<>();
    private final HeldConnection held = new HeldConnection(() -> {
        connects.incrementAndGet();
        return nextAttempt.get();
    });

    @Test
    void ping_WhenThePreviousRoundsAttemptIsStillConnecting_WaitsOnItWithoutConnectingAgain() {
        nextAttempt.set(new CompletableFuture<>());

        held.ping();
        held.discardUnlessConnecting();
        CompletableFuture<String> secondRound = held.ping();

        assertThat(connects).hasValue(1);
        assertThat(secondRound).isNotDone();
    }

    @Test
    void ping_WhenEveryConnectIsRefused_StartsOneConnectPerRound() {
        nextAttempt.set(CompletableFuture.failedFuture(new RedisConnectionException("refused")));

        for (int round = 0; round < RedisRoleProbe.FAILURE_THRESHOLD; round++) {
            assertThat(held.ping()).isCompletedExceptionally();
        }

        assertThat(connects).hasValue(RedisRoleProbe.FAILURE_THRESHOLD);
    }

    @Test
    void ping_WhenAnAttemptKeptPastTheDeadlineFailsBeforeTheNextRound_ConnectsAgain() {
        CompletableFuture<StatefulRedisConnection<String, String>> slow = new CompletableFuture<>();
        nextAttempt.set(slow);
        held.ping();
        held.discardUnlessConnecting();

        slow.completeExceptionally(new RedisConnectionException("handshake timed out"));
        nextAttempt.set(new CompletableFuture<>());
        held.ping();

        assertThat(connects).hasValue(2);
    }

    @Test
    void ping_WhenThePreviousPingFailedOnAnOpenConnection_ConnectsAgain() throws Exception {
        ClientResources clientResources = DefaultClientResources.create();
        try (PongRedis redis = new PongRedis()) {
            RedisClient client = BoundedRedisConnections.redisClient(clientResources, redis.host(), redis.port());
            HeldConnection connecting = new HeldConnection(() -> client.connectAsync(
                    StringCodec.UTF8, BoundedRedisConnections.redisUri(redis.host(), redis.port())).toCompletableFuture());
            assertThat(Hang.get(connecting.ping(), "the first PING")).isEqualTo("PONG");
            redis.refuseNextPing();
            Throwable refusal = Hang.get(connecting.ping().handle((pong, failure) -> failure), "the refused PING");
            assertThat(refusal).isNotNull();

            assertThat(Hang.get(connecting.ping(), "the PING after the refusal")).isEqualTo("PONG");

            assertThat(redis.acceptedConnections()).isEqualTo(2);
            connecting.close();
            client.shutdown();
        } finally {
            var unused = clientResources.shutdown();
        }
    }

    @Test
    void ping_WhenAPingFailsOnAConnectionAlreadyReplaced_KeepsTheReplacement() throws Exception {
        ClientResources clientResources = DefaultClientResources.create();
        try (PongRedis redis = new PongRedis()) {
            RedisClient client = BoundedRedisConnections.redisClient(clientResources, redis.host(), redis.port());
            HeldConnection connecting = new HeldConnection(() -> client.connectAsync(
                    StringCodec.UTF8, BoundedRedisConnections.redisUri(redis.host(), redis.port())).toCompletableFuture());
            assertThat(Hang.get(connecting.ping(), "the first PING")).isEqualTo("PONG");
            redis.delayNextPingReply();
            CompletableFuture<String> late = connecting.ping();
            connecting.discardUnlessConnecting();
            assertThat(Hang.get(connecting.ping(), "the PING on the replacement")).isEqualTo("PONG");
            Hang.get(late.handle((pong, failure) -> failure), "the late PING failing with its closed connection");

            assertThat(Hang.get(connecting.ping(), "the next round's PING")).isEqualTo("PONG");

            assertThat(redis.acceptedConnections()).isEqualTo(2);
            redis.releaseDelayedPingReply();
            connecting.close();
            client.shutdown();
        } finally {
            var unused = clientResources.shutdown();
        }
    }

    @Test
    void ping_WhenClosed_FailsWithoutConnecting() {
        nextAttempt.set(new CompletableFuture<>());

        held.close();

        assertThat(held.ping()).isCompletedExceptionally();
        assertThat(connects).hasValue(0);
    }

    @Test
    void close_WhenAnAttemptIsPending_StartsNoFurtherConnect() {
        nextAttempt.set(new CompletableFuture<>());
        held.ping();

        held.close();

        assertThat(held.ping()).isCompletedExceptionally();
        assertThat(connects).hasValue(1);
    }

    @Test
    void close_WhenItHoldsAnOpenConnection_ClosesItWithoutTheClientShuttingDown() throws Exception {
        ClientResources clientResources = DefaultClientResources.create();
        try (PongRedis redis = new PongRedis()) {
            RedisClient client = BoundedRedisConnections.redisClient(clientResources, redis.host(), redis.port());
            CompletableFuture<StatefulRedisConnection<String, String>> connecting = client.connectAsync(
                    StringCodec.UTF8, BoundedRedisConnections.redisUri(redis.host(), redis.port())).toCompletableFuture();
            HeldConnection open = new HeldConnection(() -> connecting);
            assertThat(Hang.get(open.ping(), "the first PING")).isEqualTo("PONG");

            open.close();

            assertThat(connecting.join().isOpen()).isFalse();
            Hang.until(() -> redis.connectionsClosedByClients() == 1, "the held connection closing");
            client.shutdown();
        } finally {
            var unused = clientResources.shutdown();
        }
    }
}
