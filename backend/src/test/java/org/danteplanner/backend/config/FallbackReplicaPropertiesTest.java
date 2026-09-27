package org.danteplanner.backend.config;

import java.util.Map;

import org.danteplanner.backend.shared.config.FallbackReplicaProperties;

import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackReplicaPropertiesTest {

    private static final String PREFIX = "datasource.fallback-replica";

    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    private FallbackReplicaProperties bind(Map<String, String> properties) {
        validator.afterPropertiesSet();
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind(PREFIX, Bindable.of(FallbackReplicaProperties.class), new ValidationBindHandler(validator))
                .orElseGet(FallbackReplicaProperties::new);
    }

    @Test
    void bind_WhenBreakerKeysSet_ReachTheBreakerConfig() {
        FallbackReplicaProperties bound = bind(Map.of(
                PREFIX + ".url", "jdbc:mysql://seoul-replica:3306/planner",
                PREFIX + ".breaker.minimum-number-of-calls", "4",
                PREFIX + ".breaker.wait-duration-in-open-state", "PT45S"));

        CircuitBreakerConfig config = bound.getBreaker().circuitBreakerConfig();

        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(45_000L);
    }

    @Test
    void bind_WhenFailureRateAboveOneHundred_IsRejected() {
        assertThatThrownBy(() -> bind(Map.of(PREFIX + ".breaker.failure-rate-threshold", "150")))
                .isInstanceOf(BindException.class);
    }

    @Test
    void bind_WhenWaitInOpenStateUnderOneSecond_IsRejected() {
        assertThatThrownBy(() -> bind(Map.of(PREFIX + ".breaker.wait-duration-in-open-state", "PT0S")))
                .isInstanceOf(BindException.class);
    }
}
