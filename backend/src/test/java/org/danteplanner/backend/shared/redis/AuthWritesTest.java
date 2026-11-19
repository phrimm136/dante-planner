package org.danteplanner.backend.shared.redis;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AuthWritesTest {

    private static final String SKIPPED = "redis.role.skipped";
    private static final String AUTH_TAG = "redis-auth";

    @Mock
    private StringRedisTemplate authStore;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void write_WhenAuthIsOpen_RefusesWithAConnectionFailureWithoutWritingAndCountsTheSkip() {
        AuthWrites writes = writesWith(RoleOutages.withOpen(RedisRole.AUTH));

        assertThatThrownBy(() -> writes.write(store -> {
            throw new AssertionError("no write may reach the store while the auth role is open");
        }))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining(RedisRole.AUTH.label());
        assertThat(skipped()).isEqualTo(1.0);
    }

    @Test
    void writeWithoutResult_WhenAuthIsOpen_RefusesWithAConnectionFailureWithoutWritingAndCountsTheSkip() {
        AuthWrites writes = writesWith(RoleOutages.withOpen(RedisRole.AUTH));

        assertThatThrownBy(() -> writes.writeWithoutResult(store -> {
            throw new AssertionError("no write may reach the store while the auth role is open");
        }))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining(RedisRole.AUTH.label());
        assertThat(skipped()).isEqualTo(1.0);
    }

    @Test
    void write_WhenAuthIsAvailable_WritesOnTheGivenStoreAndReturnsItsValue() {
        Object written = new Object();
        AtomicReference<StringRedisTemplate> reached = new AtomicReference<>();

        Object result = writesWith(new RedisRoleHealth()).write(store -> {
            reached.set(store);
            return written;
        });

        assertThat(result).isSameAs(written);
        assertThat(reached).hasValue(authStore);
        assertThat(skipped()).isEqualTo(0.0);
    }

    @Test
    void writeWithoutResult_WhenAuthIsAvailable_WritesOnTheGivenStore() {
        AtomicReference<StringRedisTemplate> reached = new AtomicReference<>();

        writesWith(new RedisRoleHealth()).writeWithoutResult(reached::set);

        assertThat(reached).hasValue(authStore);
        assertThat(skipped()).isEqualTo(0.0);
    }

    @Test
    void write_WhenTheWriteFails_PropagatesTheSameException() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("down");

        assertThatThrownBy(() -> writesWith(new RedisRoleHealth()).write(store -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void writeWithoutResult_WhenTheWriteFails_PropagatesTheSameException() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("down");

        assertThatThrownBy(() -> writesWith(new RedisRoleHealth()).writeWithoutResult(store -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void write_WhenTheWriteTimesOut_FailsWithAConnectionFailureCausedByTheTimeout() {
        QueryTimeoutException timeout = new QueryTimeoutException("Redis command timed out");

        assertThatThrownBy(() -> writesWith(new RedisRoleHealth()).write(store -> {
            throw timeout;
        }))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining(RedisRole.AUTH.label())
                .cause().isSameAs(timeout);
        assertThat(skipped()).isEqualTo(0.0);
    }

    @Test
    void writeWithoutResult_WhenTheWriteTimesOut_FailsWithAConnectionFailureCausedByTheTimeout() {
        QueryTimeoutException timeout = new QueryTimeoutException("Redis command timed out");

        assertThatThrownBy(() -> writesWith(new RedisRoleHealth()).writeWithoutResult(store -> {
            throw timeout;
        }))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining(RedisRole.AUTH.label())
                .cause().isSameAs(timeout);
        assertThat(skipped()).isEqualTo(0.0);
    }

    @Test
    void write_WhenTheWriteFailsWithAnotherDataAccessFailure_PropagatesTheSameException() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("refused");

        assertThatThrownBy(() -> writesWith(new RedisRoleHealth()).write(store -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void writeWithoutResult_WhenOnlyAuthLocalIsOpen_Writes() {
        AtomicReference<StringRedisTemplate> reached = new AtomicReference<>();

        writesWith(RoleOutages.withOpen(RedisRole.AUTH_LOCAL)).writeWithoutResult(reached::set);

        assertThat(reached).hasValue(authStore);
        assertThat(skipped()).isEqualTo(0.0);
    }

    private AuthWrites writesWith(RedisRoleHealth health) {
        return new AuthWrites(new RoleGated<>(RedisRole.AUTH, authStore, health, meters));
    }

    private double skipped() {
        Counter counter = meters.find(SKIPPED).tag("role", AUTH_TAG).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
