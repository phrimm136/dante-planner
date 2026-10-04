package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;

@ConfigurationProperties(prefix = "datasource.fallback-replica")
@Validated
@Getter
@Setter
public class FallbackReplicaProperties {

    public static final String URL_PROPERTY = "datasource.fallback-replica.url";

    public static final String POOL_NAME = "fallback-replica";
    public static final int POOL_SIZE = 3;

    public static final int SLIDING_WINDOW_CALLS = 20;
    public static final int MINIMUM_CALLS = 10;
    public static final float FAILURE_RATE_PERCENT = 50f;
    public static final Duration WAIT_IN_OPEN_STATE = Duration.ofHours(1);
    public static final int HALF_OPEN_PROBES = 3;

    public static final String PROBE_INTERVAL_PROPERTY = "datasource.fallback-replica.probe.interval";
    public static final String PROBE_INTERVAL_TEXT = "PT1S";
    public static final Duration PROBE_INTERVAL = Duration.parse(PROBE_INTERVAL_TEXT);
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);
    public static final int PROBE_FAILURE_THRESHOLD = 2;

    private String url;

    @Valid
    private Breaker breaker = new Breaker();

    @Valid
    private Probe probe = new Probe();

    @Getter
    @Setter
    public static class Breaker {

        @Min(1)
        private int slidingWindowSize = SLIDING_WINDOW_CALLS;

        @Min(1)
        private int minimumNumberOfCalls = MINIMUM_CALLS;

        @DecimalMin("1")
        @DecimalMax("100")
        private float failureRateThreshold = FAILURE_RATE_PERCENT;

        @NotNull
        @DurationMin(seconds = 1)
        private Duration waitDurationInOpenState = WAIT_IN_OPEN_STATE;

        @Min(1)
        private int permittedNumberOfCallsInHalfOpenState = HALF_OPEN_PROBES;

        public CircuitBreakerConfig circuitBreakerConfig() {
            return CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(slidingWindowSize)
                    .minimumNumberOfCalls(minimumNumberOfCalls)
                    .failureRateThreshold(failureRateThreshold)
                    .waitDurationInOpenState(waitDurationInOpenState)
                    .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                    .recordException(PrimaryReadFallbackDataSource::isConnectionFailure)
                    .ignoreException(failure -> !PrimaryReadFallbackDataSource.isConnectionFailure(failure))
                    .build();
        }
    }

    @Getter
    @Setter
    public static class Probe {

        @NotNull
        @DurationMin(millis = 1)
        private Duration interval = PROBE_INTERVAL;

        @NotNull
        @DurationMin(millis = 1)
        private Duration timeout = PROBE_TIMEOUT;

        @Min(1)
        private int failureThreshold = PROBE_FAILURE_THRESHOLD;
    }
}
