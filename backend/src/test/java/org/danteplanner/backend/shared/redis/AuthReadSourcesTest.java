package org.danteplanner.backend.shared.redis;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuthReadSourcesTest {

    private static final String SKIPPED = "redis.role.skipped";
    private static final String AUTH_LOCAL_TAG = "redis-auth-local";
    private static final String AUTH_TAG = "redis-auth";

    @Mock
    private StringRedisTemplate localStore;

    @Mock
    private StringRedisTemplate authStore;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RoleGated<StringRedisTemplate> local =
            new RoleGated<>(RedisRole.AUTH_LOCAL, null, new RedisRoleHealth(), meters);
    private final RoleGated<StringRedisTemplate> auth =
            new RoleGated<>(RedisRole.AUTH, null, new RedisRoleHealth(), meters);

    @Test
    void sourceOrder_WhenTheRegionReadsItsReplicaFirst_IsAuthLocalThenAuth() {
        assertThat(AuthReadSources.sourceOrder(false, local, auth)).containsExactly(local, auth);
    }

    @Test
    void sourceOrder_WhenTheRegionReadsThePrimaryFirst_IsAuthAlone() {
        assertThat(AuthReadSources.sourceOrder(true, local, auth)).containsExactly(auth);
    }

    @Test
    void read_WhenEveryRoleIsAvailable_ReadsTheFirstSource() {
        assertThat(sources(new RedisRoleHealth()).<StringRedisTemplate>read(store -> store, () -> null)).isSameAs(localStore);
    }

    @Test
    void read_WhenAuthLocalIsOpen_ReadsThePrimary() {
        assertThat(sources(RoleOutages.withOpen(RedisRole.AUTH_LOCAL)).<StringRedisTemplate>read(store -> store, () -> null))
                .isSameAs(authStore);
    }

    @Test
    void read_WhenEverySourcesRoleIsOpen_AnswersTheFallbackWithoutReading() {
        RedisRoleHealth bothOpen = RoleOutages.withOpen(RedisRole.AUTH_LOCAL, RedisRole.AUTH);

        assertThat(sources(bothOpen).<Object>read(store -> {
            throw new AssertionError("no store may be read while every source is open");
        }, () -> bothOpen)).isSameAs(bothOpen);
    }

    @Test
    void read_WhenTheOnlySourceIsOpen_DoesNotFallThroughToAnUnlistedRole() {
        AuthReadSources primaryOnly = new AuthReadSources(
                List.of(new RoleGated<>(RedisRole.AUTH, authStore, RoleOutages.withOpen(RedisRole.AUTH), meters)));

        assertThat(primaryOnly.<StringRedisTemplate>read(store -> store, () -> null)).isNull();
    }

    @Test
    void read_WhenAuthLocalIsOpenAndAuthAvailable_CountsOneSkipOnTheLocalGateOnly() {
        sources(RoleOutages.withOpen(RedisRole.AUTH_LOCAL)).read(store -> store, () -> null);

        assertThat(skipped(AUTH_LOCAL_TAG)).isEqualTo(1.0);
        assertThat(skipped(AUTH_TAG)).isEqualTo(0.0);
    }

    @Test
    void read_WhenBothRolesAreOpen_CountsOneSkipOnEachGate() {
        sources(RoleOutages.withOpen(RedisRole.AUTH_LOCAL, RedisRole.AUTH)).read(store -> store, () -> null);

        assertThat(skipped(AUTH_LOCAL_TAG)).isEqualTo(1.0);
        assertThat(skipped(AUTH_TAG)).isEqualTo(1.0);
    }

    private AuthReadSources sources(RedisRoleHealth health) {
        return new AuthReadSources(AuthReadSources.sourceOrder(false,
                new RoleGated<>(RedisRole.AUTH_LOCAL, localStore, health, meters),
                new RoleGated<>(RedisRole.AUTH, authStore, health, meters)));
    }

    private double skipped(String role) {
        Counter counter = meters.find(SKIPPED).tag("role", role).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
