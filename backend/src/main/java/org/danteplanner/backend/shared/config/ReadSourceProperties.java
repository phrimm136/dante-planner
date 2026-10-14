package org.danteplanner.backend.shared.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;

@ConfigurationProperties(prefix = "datasource")
@Validated
@Getter
@Setter
public class ReadSourceProperties {

    public static final String PRIMARY = "primary";

    public static final String READ_SOURCES_PROPERTY = "datasource.read-sources";

    public static final int SLIDING_WINDOW_CALLS = 20;
    public static final int MINIMUM_CALLS = 10;
    public static final float FAILURE_RATE_PERCENT = 50f;
    public static final Duration WAIT_IN_OPEN_STATE = Duration.ofHours(1);

    public static final String PROBE_INTERVAL_PROPERTY = "datasource.read-source-probe.interval";
    public static final String PROBE_INTERVAL_TEXT = "PT1S";
    public static final Duration PROBE_INTERVAL = Duration.parse(PROBE_INTERVAL_TEXT);
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);
    public static final int PROBE_FAILURE_THRESHOLD = 2;

    @NotEmpty
    @Valid
    private List<Source> readSources = new ArrayList<>();

    @Valid
    private Breaker readSourceBreaker = new Breaker();

    @Valid
    private Probe readSourceProbe = new Probe();

    @AssertTrue(message = "datasource.read-sources must hold exactly one entry named \"" + PRIMARY + "\"")
    public boolean isExactlyOnePrimary() {
        return readSources.stream().filter(Source::isPrimary).count() == 1;
    }

    @AssertTrue(message = "datasource.read-sources must not name two entries alike")
    public boolean isUniquelyNamed() {
        Set<String> names = new HashSet<>();
        return readSources.stream().allMatch(source -> names.add(source.getName()));
    }

    public boolean readsPrimaryFirst() {
        return !readSources.isEmpty() && readSources.get(0).isPrimary();
    }

    @Getter
    @Setter
    public static class Source {

        @NotBlank
        private String name;

        private String url;
        private String username;
        private String password;

        public boolean isPrimary() {
            return PRIMARY.equals(name);
        }

        @AssertTrue(message = "a read source other than \"" + PRIMARY + "\" needs a url, and \""
                + PRIMARY + "\" takes none: it reads through the primary pool")
        public boolean isEndpointMatchingItsRole() {
            return isPrimary() ? url == null && username == null && password == null : StringUtils.hasText(url);
        }
    }

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
