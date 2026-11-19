package org.danteplanner.backend.shared.sse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RedisRoleProbe;
import org.danteplanner.backend.shared.redis.RoomyProbeTimeouts;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.PongRedis;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class SseSubscriptionTest {

    private final AtomicBoolean listening = new AtomicBoolean();
    private final AtomicInteger resubscribes = new AtomicInteger();
    private final List<Runnable> held = new ArrayList<>();
    private final Executor holding = held::add;

    @Test
    void connect_WhenListening_CompletesWithoutAnAttempt() {
        listening.set(true);
        SseSubscription subscription = subscription(resubscribes::incrementAndGet, holding);

        CompletableFuture<Void> connected = subscription.connect();

        assertThat(connected).isCompleted();
        assertThat(connected.isCompletedExceptionally()).isFalse();
        assertThat(resubscribes).hasValue(0);
        assertThat(held).isEmpty();
    }

    @Test
    void connect_WhenNotListening_StartsOneAttemptSharedByEveryCallUntilItEnds() {
        SseSubscription subscription = subscription(resubscribes::incrementAndGet, holding);

        CompletableFuture<Void> first = subscription.connect();
        CompletableFuture<Void> second = subscription.connect();
        CompletableFuture<Void> third = subscription.connect();

        assertThat(held).hasSize(1);
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
        assertThat(first).isNotDone();

        held.getFirst().run();

        assertThat(first).isCompleted();
        assertThat(first.isCompletedExceptionally()).isFalse();
        assertThat(resubscribes).hasValue(1);
    }

    @Test
    void connect_WhenTheLastAttemptFailed_StartsAnotherAttempt() {
        IllegalStateException refused = new IllegalStateException("refused");
        SseSubscription subscription = subscription(() -> {
            resubscribes.incrementAndGet();
            throw refused;
        }, holding);

        CompletableFuture<Void> failed = subscription.connect();
        held.getFirst().run();

        assertThat(failed.isCompletedExceptionally()).isTrue();
        assertThat(catchThrowable(failed::join)).isInstanceOf(CompletionException.class).cause().isSameAs(refused);

        CompletableFuture<Void> retried = subscription.connect();

        assertThat(held).hasSize(2);
        assertThat(retried).isNotSameAs(failed).isNotDone();
    }

    @Test
    void connect_WhenClosed_FailsAtOnceWithoutAnAttempt() {
        SseSubscription subscription = subscription(resubscribes::incrementAndGet, holding);

        subscription.close();
        CompletableFuture<Void> notListening = subscription.connect();
        listening.set(true);
        CompletableFuture<Void> whileListening = subscription.connect();

        assertThat(notListening.isCompletedExceptionally()).isTrue();
        assertThat(whileListening.isCompletedExceptionally()).isTrue();
        assertThat(held).isEmpty();
        assertThat(resubscribes).hasValue(0);
    }

    @Test
    void connect_WhenTheExecutorRejectsTheAttempt_FailsWithTheRejectionAndTriesAgainNextTime() {
        RejectedExecutionException rejected = new RejectedExecutionException("shut down");
        AtomicInteger submissions = new AtomicInteger();
        SseSubscription subscription = subscription(resubscribes::incrementAndGet, task -> {
            submissions.incrementAndGet();
            throw rejected;
        });

        CompletableFuture<Void> first = subscription.connect();

        assertThat(first.isCompletedExceptionally()).isTrue();
        assertThat(catchThrowable(first::join)).isInstanceOf(CompletionException.class).cause().isSameAs(rejected);

        CompletableFuture<Void> second = subscription.connect();

        assertThat(second.isCompletedExceptionally()).isTrue();
        assertThat(submissions).hasValue(2);
        assertThat(resubscribes).hasValue(0);
    }

    @Test
    void role_WhenAsked_IsSseLocal() {
        assertThat(subscription(resubscribes::incrementAndGet, holding).role()).isEqualTo(RedisRole.SSE_LOCAL);
    }

    @Test
    void probe_WhenTheSubscriptionCannotBeMadeAndThenCan_OpensSseLocalAloneAndClosesIt() throws IOException {
        AtomicBoolean refusing = new AtomicBoolean(true);
        SseSubscription subscription = subscription(() -> {
            if (refusing.get()) {
                throw new IllegalStateException("refused");
            }
            listening.set(true);
        }, Runnable::run);
        RedisRoleHealth health = new RedisRoleHealth();
        ClientResources clientResources = DefaultClientResources.create();

        try (PongRedis redis = new PongRedis();
                RedisRoleProbe probe = new RedisRoleProbe(clientResources,
                        RedisEndpoints.everyRoleAt(redis.host(), redis.port()), health, List.of(subscription),
                        RoomyProbeTimeouts.BOTH_PHASES)) {
            probe.probeAtStartup();

            assertThat(EnumSet.allOf(RedisRole.class)).filteredOn(role -> !health.isAvailable(role))
                    .containsExactly(RedisRole.SSE_LOCAL);

            refusing.set(false);
            Hang.until(() -> {
                probe.probe();
                return health.isAvailable(RedisRole.SSE_LOCAL);
            }, "the probe closing sse-local once the subscription can be made");
        } finally {
            var unused = clientResources.shutdown();
        }

        assertThat(listening).isTrue();
    }

    private SseSubscription subscription(Runnable resubscribe, Executor attempts) {
        return new SseSubscription(listening::get, resubscribe, attempts);
    }
}
