package org.danteplanner.backend.shared.redis;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisRoleStartupTest {

    private static final String BREAKER_STATE_GAUGE = "resilience4j.circuitbreaker.state";

    @Autowired
    private RedisRoleHealth roleHealth;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void roleHealth_WhenEveryEndpointIsUnreachableAtContextStart_LoadsTheContextWithEveryRoleUnavailable() {
        assertThat(RedisRole.values()).noneMatch(roleHealth::isAvailable);
    }

    @Test
    void breakerStateGauge_WhenEveryRoleIsOpenWithRoutingDisabled_ReadsOpenForEveryRoleBreaker() {
        assertThat(RedisRole.values()).allSatisfy(role -> assertThat(openGauge(role))
                .as(role.breakerName())
                .isNotNull()
                .extracting(Gauge::value)
                .isEqualTo(1.0));
    }

    private Gauge openGauge(RedisRole role) {
        return meterRegistry.find(BREAKER_STATE_GAUGE)
                .tag("name", role.breakerName())
                .tag("state", CircuitBreaker.State.OPEN.name().toLowerCase(Locale.ROOT))
                .gauge();
    }
}
