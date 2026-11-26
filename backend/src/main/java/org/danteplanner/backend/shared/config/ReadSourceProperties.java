package org.danteplanner.backend.shared.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.hibernate.validator.constraints.time.DurationMin;

@ConfigurationProperties(prefix = "datasource")
@Validated
public record ReadSourceProperties(
        @NotEmpty @DefaultValue List<@Valid Source> readSources,
        @Valid @DefaultValue Breaker readSourceBreaker,
        @Valid @DefaultValue Probe readSourceProbe) {

    public static final String PRIMARY = "primary";

    public static final String READ_SOURCES_PROPERTY = "datasource.read-sources";

    public static final int SLIDING_WINDOW_CALLS = 20;
    public static final int MINIMUM_CALLS = 10;
    public static final float FAILURE_RATE_PERCENT = 50f;
    public static final String WAIT_IN_OPEN_STATE_TEXT = "PT1H";
    public static final Duration WAIT_IN_OPEN_STATE = Duration.parse(WAIT_IN_OPEN_STATE_TEXT);

    public static final String PROBE_INTERVAL_PROPERTY = "datasource.read-source-probe.interval";
    public static final String PROBE_INTERVAL_TEXT = "PT1S";
    public static final Duration PROBE_INTERVAL = Duration.parse(PROBE_INTERVAL_TEXT);
    public static final int PROBE_FAILURE_THRESHOLD = 2;

    private static final String SLIDING_WINDOW_CALLS_TEXT = "" + SLIDING_WINDOW_CALLS;
    private static final String MINIMUM_CALLS_TEXT = "" + MINIMUM_CALLS;
    private static final String FAILURE_RATE_PERCENT_TEXT = "" + FAILURE_RATE_PERCENT;
    private static final String PROBE_TIMEOUT_TEXT = "PT1S";
    private static final String PROBE_FAILURE_THRESHOLD_TEXT = "" + PROBE_FAILURE_THRESHOLD;

    @AssertTrue(message = "datasource.read-sources must hold exactly one entry named \"" + PRIMARY + "\"")
    public boolean isExactlyOnePrimary() {
        return readSources.stream().filter(Source::isPrimary).count() == 1;
    }

    @AssertTrue(message = "datasource.read-sources must not name two entries alike")
    public boolean isUniquelyNamed() {
        Set<String> names = new HashSet<>();
        return readSources.stream().allMatch(source -> names.add(source.name()));
    }

    public enum ReadOrder {
        PRIMARY_FIRST,
        REPLICA_FIRST;

        public <T> List<T> stores(T regional, T primary) {
            return switch (this) {
                case PRIMARY_FIRST -> List.of(primary);
                case REPLICA_FIRST -> List.of(regional, primary);
            };
        }
    }

    public ReadOrder readOrder() {
        return !readSources.isEmpty() && readSources.get(0).isPrimary()
                ? ReadOrder.PRIMARY_FIRST
                : ReadOrder.REPLICA_FIRST;
    }

    public record Source(
            @NotBlank @DefaultValue("") String name,
            @DefaultValue("") String url,
            @DefaultValue("") String username,
            @DefaultValue("") String password) {

        public sealed interface Endpoint {

            record Primary() implements Endpoint {
            }

            record Replica(String url, String username, String password) implements Endpoint {
            }
        }

        public boolean isPrimary() {
            return PRIMARY.equals(name);
        }

        public Endpoint endpoint() {
            return isPrimary() ? new Endpoint.Primary() : new Endpoint.Replica(url, username, password);
        }

        @AssertTrue(message = "a read source other than \"" + PRIMARY + "\" needs a url, and \""
                + PRIMARY + "\" takes none: it reads through the primary pool")
        public boolean isEndpointMatchingItsRole() {
            return isPrimary()
                    ? url.isEmpty() && username.isEmpty() && password.isEmpty()
                    : StringUtils.hasText(url);
        }
    }

    public record Breaker(
            @Min(1) @DefaultValue(SLIDING_WINDOW_CALLS_TEXT) int slidingWindowSize,
            @Min(1) @DefaultValue(MINIMUM_CALLS_TEXT) int minimumNumberOfCalls,
            @DecimalMin("1") @DecimalMax("100") @DefaultValue(FAILURE_RATE_PERCENT_TEXT) float failureRateThreshold,
            @NotNull @DurationMin(seconds = 1) @DefaultValue(WAIT_IN_OPEN_STATE_TEXT) Duration waitDurationInOpenState) {

        public static Breaker defaults() {
            return new Breaker(SLIDING_WINDOW_CALLS, MINIMUM_CALLS, FAILURE_RATE_PERCENT, WAIT_IN_OPEN_STATE);
        }

        public CircuitBreakerConfig circuitBreakerConfig() {
            return CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(slidingWindowSize)
                    .minimumNumberOfCalls(minimumNumberOfCalls)
                    .failureRateThreshold(failureRateThreshold)
                    .waitDurationInOpenState(waitDurationInOpenState)
                    .build();
        }
    }

    public record Probe(
            @NotNull @DurationMin(millis = 1) @DefaultValue(PROBE_INTERVAL_TEXT) Duration interval,
            @NotNull @DurationMin(millis = 1) @DefaultValue(PROBE_TIMEOUT_TEXT) Duration timeout,
            @Min(1) @DefaultValue(PROBE_FAILURE_THRESHOLD_TEXT) int failureThreshold) {

        public static Probe defaults() {
            return new Probe(PROBE_INTERVAL, Duration.parse(PROBE_TIMEOUT_TEXT), PROBE_FAILURE_THRESHOLD);
        }
    }
}
