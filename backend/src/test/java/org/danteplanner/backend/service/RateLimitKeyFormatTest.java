package org.danteplanner.backend.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.danteplanner.backend.shared.ratelimit.ChargeKey;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.support.InMemoryProxyManager;

import org.danteplanner.backend.support.RateLimitStores;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes what a live production bucket is called and which configured bucket it draws from.
 *
 * <p>A bucket's state lives in Redis under its key, so respelling a key hands every currently
 * limited caller a full allowance — an invisible outage of the limit itself. Drawing from the
 * wrong configured bucket silently retunes a limit the same way. Both are pinned here per
 * {@link RateLimitPolicy}, against an in-memory bucket4j proxy manager that records the key
 * and configuration the service asks for.</p>
 *
 * <p>The row set is checked against {@link RateLimitPolicy#values()}, so a policy added without
 * a pinned key fails rather than shipping unpinned.</p>
 */
class RateLimitKeyFormatTest {

    private static final UUID DEVICE_ID = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

    private static final long CRUD_CAPACITY = 11;
    private static final long SSE_CAPACITY = 13;
    private static final long AUTH_CAPACITY = 14;
    private static final long COMMENT_CAPACITY = 15;
    private static final long REPORT_CAPACITY = 16;
    private static final long MODERATION_CAPACITY = 17;
    private static final long PUBLIC_READ_CAPACITY = 18;
    private static final long PLANNER_STATS_CAPACITY = 19;
    private static final long VIEW_RECORD_CAPACITY = 20;

    private static final Long USER_ID = 7L;

    /**
     * One frozen policy: the exact bucket key it consumes, the configured bucket it draws that
     * key's shape from (identified by its unique capacity), and the 429 payload it raises.
     */
    private record FrozenKey(
            RateLimitPolicy policy,
            String key,
            long capacity,
            String rejectedUser,
            String rejectedEndpoint,
            ChargeKey chargeKey) {
    }

    private static final List<FrozenKey> FROZEN_KEYS = List.of(
            new FrozenKey(RateLimitPolicy.CRUD, "7:vote", CRUD_CAPACITY, String.valueOf(USER_ID), "vote",
                    ChargeKey.User.parse(RateLimitPolicy.CRUD, USER_ID, "vote")),
            new FrozenKey(RateLimitPolicy.SSE, "7:sse", SSE_CAPACITY, String.valueOf(USER_ID), "sse",
                    ChargeKey.User.parse(RateLimitPolicy.SSE, USER_ID, "")),
            new FrozenKey(RateLimitPolicy.COMMENT, "7:comment", COMMENT_CAPACITY, String.valueOf(USER_ID), "comment",
                    ChargeKey.User.parse(RateLimitPolicy.COMMENT, USER_ID, "")),
            new FrozenKey(RateLimitPolicy.REPORT, "7:report", REPORT_CAPACITY, String.valueOf(USER_ID), "report",
                    ChargeKey.User.parse(RateLimitPolicy.REPORT, USER_ID, "")),
            new FrozenKey(RateLimitPolicy.MODERATION, "7:moderation", MODERATION_CAPACITY, String.valueOf(USER_ID), "moderation",
                    ChargeKey.User.parse(RateLimitPolicy.MODERATION, USER_ID, "")),
            new FrozenKey(RateLimitPolicy.AUTH, "ip:203.0.113.9:auth", AUTH_CAPACITY, "null", "auth",
                    ChargeKey.Client.parse(RateLimitPolicy.AUTH, "ip:203.0.113.9")),
            new FrozenKey(RateLimitPolicy.PLANNER_COMMENT_SSE,
                    "device:3f2504e0-4f89-41d3-9a0c-0305e82c3301:planner-comment-sse",
                    SSE_CAPACITY, "null", "planner-comment-sse",
                    ChargeKey.Client.parse(RateLimitPolicy.PLANNER_COMMENT_SSE, "device:" + DEVICE_ID)),
            new FrozenKey(RateLimitPolicy.PUBLIC_READ, "ip:203.0.113.9:public-read",
                    PUBLIC_READ_CAPACITY, "null", "public-read",
                    ChargeKey.Client.parse(RateLimitPolicy.PUBLIC_READ, "ip:203.0.113.9")),
            new FrozenKey(RateLimitPolicy.PLANNER_STATS, "ip:203.0.113.9:planner-stats",
                    PLANNER_STATS_CAPACITY, "null", "planner-stats",
                    ChargeKey.Client.parse(RateLimitPolicy.PLANNER_STATS, "ip:203.0.113.9")),
            new FrozenKey(RateLimitPolicy.VIEW_RECORD, "ip:203.0.113.9:view-record",
                    VIEW_RECORD_CAPACITY, "null", "view-record",
                    ChargeKey.Client.parse(RateLimitPolicy.VIEW_RECORD, "ip:203.0.113.9")));

    @TestFactory
    Stream<DynamicTest> policy_WhenCharged_ConsumesFrozenBucketKey() {
        return FROZEN_KEYS.stream().map(frozen -> DynamicTest.dynamicTest(frozen.policy().name(), () -> {
            InMemoryProxyManager allowing = new InMemoryProxyManager();
            new RateLimitService(allowing, properties()).charge(frozen.policy(), frozen.chargeKey());

            assertThat(allowing.charges()).singleElement().satisfies(charge -> {
                assertThat(charge.key()).isEqualTo(frozen.key());
                assertThat(charge.configuration().getBandwidths()[0].getCapacity()).isEqualTo(frozen.capacity());
            });

            InMemoryProxyManager refusing = new InMemoryProxyManager();
            refusing.denyNext(1);
            RateLimitService refused = new RateLimitService(refusing, properties());

            assertThat(refused.charge(frozen.policy(), frozen.chargeKey()).isConsumed()).isFalse();
            RateLimitExceededException exceeded = frozen.chargeKey().refusal(1);
            assertThat(exceeded.getMessage())
                    .startsWith("Rate limit exceeded for user " + frozen.rejectedUser() + " on");
            assertThat(exceeded.getEndpoint()).isEqualTo(frozen.rejectedEndpoint());
        }));
    }

    @Test
    void policy_WhenKeyNotFrozen_IsRejected() {
        Set<RateLimitPolicy> frozen = FROZEN_KEYS.stream()
                .map(FrozenKey::policy)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RateLimitPolicy.class)));

        assertThat(frozen).isEqualTo(EnumSet.allOf(RateLimitPolicy.class));
    }

    @Test
    void policy_WhenFrozenForTheSameClient_OwnsADistinctKey() {
        List<String> clientKeys = FROZEN_KEYS.stream()
                .map(FrozenKey::key)
                .filter(key -> key.startsWith("ip:203.0.113.9:"))
                .toList();

        assertThat(clientKeys)
                .contains("ip:203.0.113.9:public-read", "ip:203.0.113.9:planner-stats",
                        "ip:203.0.113.9:view-record")
                .doesNotHaveDuplicates();
    }

    /**
     * Distinct capacities so a policy drawing from the wrong configured bucket is visible rather
     * than indistinguishable.
     */
    private static RateLimitProperties properties() {
        return RateLimitStores.limits(0, Map.ofEntries(
                Map.entry(RateLimitPolicy.CRUD, bucket(CRUD_CAPACITY)),
                Map.entry(RateLimitPolicy.SSE, bucket(SSE_CAPACITY)),
                Map.entry(RateLimitPolicy.AUTH, bucket(AUTH_CAPACITY)),
                Map.entry(RateLimitPolicy.COMMENT, bucket(COMMENT_CAPACITY)),
                Map.entry(RateLimitPolicy.REPORT, bucket(REPORT_CAPACITY)),
                Map.entry(RateLimitPolicy.MODERATION, bucket(MODERATION_CAPACITY)),
                Map.entry(RateLimitPolicy.PUBLIC_READ, bucket(PUBLIC_READ_CAPACITY)),
                Map.entry(RateLimitPolicy.PLANNER_STATS, bucket(PLANNER_STATS_CAPACITY)),
                Map.entry(RateLimitPolicy.VIEW_RECORD, bucket(VIEW_RECORD_CAPACITY))));
    }

    private static RateLimitProperties.BucketConfig bucket(long capacity) {
        RateLimitProperties.BucketConfig config = new RateLimitProperties.BucketConfig((int) capacity, (int) capacity, 60);
        return config;
    }
}
