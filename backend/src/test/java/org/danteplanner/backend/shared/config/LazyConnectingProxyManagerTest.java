package org.danteplanner.backend.shared.config;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LazyConnectingProxyManagerTest {

    private static final Duration FAIL_FAST_BUDGET = Duration.ofMillis(100);
    private static final long LATCH_WAIT_SECONDS = 5;

    @Test
    void builder_WhenAnotherCallerIsConnecting_FailsFastWithoutCallingTheConnector() throws Exception {
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger connectorCalls = new AtomicInteger();
        Supplier<ProxyManager<byte[]>> blockingConnector = () -> {
            connectorCalls.incrementAndGet();
            connecting.countDown();
            awaitQuietly(release);
            throw new RedisConnectionException("connect refused");
        };
        RedisConnectionConfig.LazyConnectingProxyManager manager =
                new RedisConnectionConfig.LazyConnectingProxyManager(blockingConnector);
        Thread holder = Thread.ofPlatform().start(() -> {
            var unused = catchThrowable(manager::builder);
        });
        assertThat(connecting.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        try {
            assertTimeoutPreemptively(FAIL_FAST_BUDGET, () -> assertThatThrownBy(manager::builder)
                    .isInstanceOf(RedisException.class));
            assertThat(connectorCalls).hasValue(1);
        } finally {
            release.countDown();
            holder.join(TimeUnit.SECONDS.toMillis(LATCH_WAIT_SECONDS));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void builder_WhenTheConnectFailed_RetriesOnTheNextCall() {
        ProxyManager<byte[]> connected = mock(ProxyManager.class);
        RemoteBucketBuilder<byte[]> bucketBuilder = mock(RemoteBucketBuilder.class);
        when(connected.builder()).thenReturn(bucketBuilder);
        AtomicInteger connectorCalls = new AtomicInteger();
        RedisConnectionConfig.LazyConnectingProxyManager manager =
                new RedisConnectionConfig.LazyConnectingProxyManager(() -> {
                    if (connectorCalls.incrementAndGet() == 1) {
                        throw new RedisConnectionException("connect refused");
                    }
                    return connected;
                });

        assertThatThrownBy(manager::builder).isInstanceOf(RedisConnectionException.class);

        assertThat(manager.builder()).isSameAs(bucketBuilder);
        assertThat(connectorCalls).hasValue(2);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
