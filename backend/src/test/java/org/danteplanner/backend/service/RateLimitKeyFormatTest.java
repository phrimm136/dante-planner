package org.danteplanner.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.danteplanner.backend.shared.config.RateLimitProperties;
import org.danteplanner.backend.shared.exception.RateLimitExceededException;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Freezes what a live production bucket is called and which configured bucket it draws from.
 *
 * <p>A bucket's state lives in Redis under its key, so respelling a key hands every currently
 * limited caller a full allowance — an invisible outage of the limit itself. Drawing from the
 * wrong configured bucket silently retunes a limit the same way. Both are pinned here per
 * {@link RateLimitPolicy}, against a bucket4j {@link ProxyManager} double that records the key
 * and configuration the service asks for.</p>
 *
 * <p>The row set is checked against {@link RateLimitPolicy#values()}, so a policy added without
 * a pinned key fails rather than shipping unpinned.</p>
 */
class RateLimitKeyFormatTest {

    private static final UUID DEVICE_ID = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

    private static final long CRUD_CAPACITY = 11;
    private static final long IMPORT_CAPACITY = 12;
    private static final long SSE_CAPACITY = 13;
    private static final long AUTH_CAPACITY = 14;
    private static final long COMMENT_CAPACITY = 15;
    private static final long REPORT_CAPACITY = 16;
    private static final long MODERATION_CAPACITY = 17;
    private static final long PUBLIC_READ_CAPACITY = 18;

    private static final Long USER_ID = 7L;

    /**
     * One frozen policy: the exact bucket key it consumes, the configured bucket it draws that
     * key's shape from (identified by its unique capacity), and the 429 payload it raises.
     */
    private record FrozenKey(
            RateLimitPolicy policy,
            String key,
            long capacity,
            Long rejectedUserId,
            String rejectedEndpoint,
            Consumer<RateLimitService> charge) {
    }

    private static final List<FrozenKey> FROZEN_KEYS = List.of(
            new FrozenKey(RateLimitPolicy.CRUD, "7:vote", CRUD_CAPACITY, USER_ID, "vote",
                    service -> service.check(RateLimitPolicy.CRUD, USER_ID, "vote")),
            new FrozenKey(RateLimitPolicy.IMPORT, "7:import", IMPORT_CAPACITY, USER_ID, "import",
                    service -> service.check(RateLimitPolicy.IMPORT, USER_ID)),
            new FrozenKey(RateLimitPolicy.SSE, "7:sse", SSE_CAPACITY, USER_ID, "sse",
                    service -> service.check(RateLimitPolicy.SSE, USER_ID)),
            new FrozenKey(RateLimitPolicy.COMMENT, "7:comment", COMMENT_CAPACITY, USER_ID, "comment",
                    service -> service.check(RateLimitPolicy.COMMENT, USER_ID)),
            new FrozenKey(RateLimitPolicy.REPORT, "7:report", REPORT_CAPACITY, USER_ID, "report",
                    service -> service.check(RateLimitPolicy.REPORT, USER_ID)),
            new FrozenKey(RateLimitPolicy.MODERATION, "7:moderation", MODERATION_CAPACITY, USER_ID, "moderation",
                    service -> service.check(RateLimitPolicy.MODERATION, USER_ID)),
            new FrozenKey(RateLimitPolicy.AUTH, "ip:203.0.113.9:auth", AUTH_CAPACITY, null, "auth",
                    service -> service.check(RateLimitPolicy.AUTH, "ip:203.0.113.9")),
            new FrozenKey(RateLimitPolicy.PLANNER_COMMENT_SSE,
                    "device:3f2504e0-4f89-41d3-9a0c-0305e82c3301:planner-comment-sse",
                    SSE_CAPACITY, null, "planner-comment-sse",
                    service -> service.check(
                            RateLimitPolicy.PLANNER_COMMENT_SSE, "device:" + DEVICE_ID)),
            new FrozenKey(RateLimitPolicy.PUBLIC_READ, "ip:203.0.113.9:public-read",
                    PUBLIC_READ_CAPACITY, null, "public-read",
                    service -> service.check(RateLimitPolicy.PUBLIC_READ, "ip:203.0.113.9")));

    @TestFactory
    Stream<DynamicTest> policy_WhenCharged_ConsumesFrozenBucketKey() {
        return FROZEN_KEYS.stream().map(frozen -> DynamicTest.dynamicTest(frozen.policy().name(), () -> {
            BucketRecorder allowing = new BucketRecorder(true);
            frozen.charge().accept(new RateLimitService(allowing.proxyManager, properties()));

            assertThat(allowing.consumedKey()).isEqualTo(frozen.key());
            assertThat(allowing.consumedCapacity()).isEqualTo(frozen.capacity());

            BucketRecorder refusing = new BucketRecorder(false);
            RateLimitService refused = new RateLimitService(refusing.proxyManager, properties());

            assertThatThrownBy(() -> frozen.charge().accept(refused))
                    .isInstanceOf(RateLimitExceededException.class)
                    .satisfies(thrown -> {
                        RateLimitExceededException exceeded = (RateLimitExceededException) thrown;
                        assertThat(exceeded.getUserId()).isEqualTo(frozen.rejectedUserId());
                        assertThat(exceeded.getEndpoint()).isEqualTo(frozen.rejectedEndpoint());
                    });
        }));
    }

    @Test
    void policy_WhenKeyNotFrozen_IsRejected() {
        Set<RateLimitPolicy> frozen = FROZEN_KEYS.stream()
                .map(FrozenKey::policy)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RateLimitPolicy.class)));

        assertThat(frozen).isEqualTo(EnumSet.allOf(RateLimitPolicy.class));
    }

    /**
     * Distinct capacities so a policy drawing from the wrong configured bucket is visible rather
     * than indistinguishable.
     */
    private static RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCrud(bucket(CRUD_CAPACITY));
        properties.setImportConfig(bucket(IMPORT_CAPACITY));
        properties.setSse(bucket(SSE_CAPACITY));
        properties.setAuth(bucket(AUTH_CAPACITY));
        properties.setComment(bucket(COMMENT_CAPACITY));
        properties.setReport(bucket(REPORT_CAPACITY));
        properties.setModeration(bucket(MODERATION_CAPACITY));
        properties.setPublicRead(bucket(PUBLIC_READ_CAPACITY));
        return properties;
    }

    private static RateLimitProperties.BucketConfig bucket(long capacity) {
        RateLimitProperties.BucketConfig config = new RateLimitProperties.BucketConfig();
        config.setCapacity((int) capacity);
        config.setRefillTokens((int) capacity);
        config.setRefillDurationSeconds(60);
        return config;
    }

    /**
     * Stands in for the bucket4j proxy manager — a third-party boundary — and records the key
     * bytes and bucket configuration the service asked it to build.
     */
    private static final class BucketRecorder {

        private final ProxyManager<byte[]> proxyManager;
        private final RemoteBucketBuilder<byte[]> builder;

        @SuppressWarnings("unchecked")
        private BucketRecorder(boolean allow) {
            proxyManager = mock(ProxyManager.class);
            builder = mock(RemoteBucketBuilder.class);
            BucketProxy bucketProxy = mock(BucketProxy.class);
            when(proxyManager.builder()).thenReturn(builder);
            when(builder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucketProxy);
            when(bucketProxy.tryConsume(anyLong())).thenReturn(allow);
        }

        @SuppressWarnings("unchecked")
        private String consumedKey() {
            ArgumentCaptor<byte[]> key = ArgumentCaptor.forClass(byte[].class);
            verify(builder).build(key.capture(), any(Supplier.class));
            return new String(key.getValue(), StandardCharsets.UTF_8);
        }

        @SuppressWarnings("unchecked")
        private long consumedCapacity() {
            ArgumentCaptor<Supplier<BucketConfiguration>> configuration =
                    ArgumentCaptor.forClass(Supplier.class);
            verify(builder).build(any(byte[].class), configuration.capture());
            return configuration.getValue().get().getBandwidths()[0].getCapacity();
        }
    }
}
