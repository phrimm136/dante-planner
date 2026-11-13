package org.danteplanner.backend.shared.redis;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuthReadSourcesTest {

    @Mock
    private StringRedisTemplate localStore;

    @Mock
    private StringRedisTemplate authStore;

    private final AuthReadSources.Source local = new AuthReadSources.Source(RedisRole.AUTH_LOCAL, null);
    private final AuthReadSources.Source auth = new AuthReadSources.Source(RedisRole.AUTH, null);

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
                List.of(new AuthReadSources.Source(RedisRole.AUTH, authStore)), RoleOutages.withOpen(RedisRole.AUTH));

        assertThat(primaryOnly.<StringRedisTemplate>read(store -> store, () -> null)).isNull();
    }

    private AuthReadSources sources(RedisRoleHealth health) {
        return new AuthReadSources(AuthReadSources.sourceOrder(false,
                new AuthReadSources.Source(RedisRole.AUTH_LOCAL, localStore),
                new AuthReadSources.Source(RedisRole.AUTH, authStore)), health);
    }
}
