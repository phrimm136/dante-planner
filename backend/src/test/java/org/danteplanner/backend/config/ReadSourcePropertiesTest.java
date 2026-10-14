package org.danteplanner.backend.config;

import java.util.HashMap;
import java.util.Map;

import org.danteplanner.backend.shared.config.ReadSourceProperties;

import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadSourcePropertiesTest {

    private static final String PREFIX = "datasource";
    private static final String SOURCES = PREFIX + ".read-sources";
    private static final String REPLICA = "seoul-replica";
    private static final String REPLICA_URL = "jdbc:mysql://seoul-replica:3306/planner";

    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    private ReadSourceProperties bind(Binder binder) {
        validator.afterPropertiesSet();
        return binder.bind(PREFIX, Bindable.of(ReadSourceProperties.class), new ValidationBindHandler(validator))
                .orElseGet(ReadSourceProperties::new);
    }

    private ReadSourceProperties bind(Map<String, String> properties) {
        return bind(new Binder(new MapConfigurationPropertySource(properties)));
    }

    private static Map<String, String> seoulSources() {
        Map<String, String> properties = new HashMap<>();
        properties.put(SOURCES + "[0].name", REPLICA);
        properties.put(SOURCES + "[0].url", REPLICA_URL);
        properties.put(SOURCES + "[1].name", ReadSourceProperties.PRIMARY);
        return properties;
    }

    @Test
    void bind_WhenSeoulListSet_KeepsItsOrderAndReadsTheReplicaFirst() {
        ReadSourceProperties bound = bind(seoulSources());

        assertThat(bound.getReadSources())
                .extracting(ReadSourceProperties.Source::getName)
                .containsExactly(REPLICA, ReadSourceProperties.PRIMARY);
        assertThat(bound.getReadSources().get(0).getUrl()).isEqualTo(REPLICA_URL);
        assertThat(bound.readsPrimaryFirst()).isFalse();
    }

    @Test
    void bind_WhenSetThroughEnvironmentVariables_BindsTheSameOrderedList() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("DATASOURCE_READSOURCES_0_NAME", ReadSourceProperties.PRIMARY);
        variables.put("DATASOURCE_READSOURCES_1_NAME", REPLICA);
        variables.put("DATASOURCE_READSOURCES_1_URL", REPLICA_URL);
        variables.put("DATASOURCE_READSOURCES_1_USERNAME", "replicaUser");
        variables.put("DATASOURCE_READSOURCEBREAKER_MINIMUMNUMBEROFCALLS", "4");
        SystemEnvironmentPropertySource environment = new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables);

        ReadSourceProperties bound = bind(new Binder(ConfigurationPropertySources.from(environment)));

        assertThat(bound.getReadSources())
                .extracting(ReadSourceProperties.Source::getName)
                .containsExactly(ReadSourceProperties.PRIMARY, REPLICA);
        assertThat(bound.getReadSources().get(1).getUrl()).isEqualTo(REPLICA_URL);
        assertThat(bound.getReadSources().get(1).getUsername()).isEqualTo("replicaUser");
        assertThat(bound.getReadSourceBreaker().getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(bound.readsPrimaryFirst()).isTrue();
    }

    @Test
    void bind_WhenNoEntryIsNamedPrimary_IsRejected() {
        Map<String, String> properties = new HashMap<>();
        properties.put(SOURCES + "[0].name", REPLICA);
        properties.put(SOURCES + "[0].url", REPLICA_URL);

        assertThatThrownBy(() -> bind(properties))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("must hold exactly one entry named \"primary\"");
    }

    @Test
    void bind_WhenTwoEntriesAreNamedPrimary_IsRejected() {
        Map<String, String> properties = seoulSources();
        properties.put(SOURCES + "[2].name", ReadSourceProperties.PRIMARY);

        assertThatThrownBy(() -> bind(properties))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("must hold exactly one entry named \"primary\"");
    }

    @Test
    void bind_WhenTheListIsMissing_IsRejected() {
        assertThatThrownBy(() -> bind(Map.of(PREFIX + ".read-source-probe.failure-threshold", "2")))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("on field 'readSources'");
    }

    @Test
    void bind_WhenAReplicaHasNoUrl_IsRejected() {
        Map<String, String> properties = seoulSources();
        properties.remove(SOURCES + "[0].url");

        assertThatThrownBy(() -> bind(properties))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("needs a url");
    }

    @Test
    void bind_WhenThePrimaryCarriesAUrl_IsRejected() {
        Map<String, String> properties = seoulSources();
        properties.put(SOURCES + "[1].url", REPLICA_URL);

        assertThatThrownBy(() -> bind(properties))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("takes none");
    }

    @Test
    void bind_WhenTwoEntriesShareAName_IsRejected() {
        Map<String, String> properties = seoulSources();
        properties.put(SOURCES + "[2].name", REPLICA);
        properties.put(SOURCES + "[2].url", REPLICA_URL);

        assertThatThrownBy(() -> bind(properties))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("must not name two entries alike");
    }

    @Test
    void bind_WhenBreakerKeysSet_ReachTheBreakerConfig() {
        Map<String, String> properties = seoulSources();
        properties.put(PREFIX + ".read-source-breaker.minimum-number-of-calls", "4");
        properties.put(PREFIX + ".read-source-breaker.wait-duration-in-open-state", "PT45S");

        CircuitBreakerConfig config = bind(properties).getReadSourceBreaker().circuitBreakerConfig();

        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(45_000L);
    }

    @Test
    void bind_WhenFailureRateAboveOneHundred_IsRejected() {
        Map<String, String> properties = seoulSources();
        properties.put(PREFIX + ".read-source-breaker.failure-rate-threshold", "150");

        assertThatThrownBy(() -> bind(properties))
                .isInstanceOf(BindException.class)
                .cause()
                .hasMessageContaining("on field 'failureRateThreshold': rejected value [150.0]");
    }

    @Test
    void breaker_WhenDefaulted_WaitsAnHourInOpenSoUserReadsNeverHalfOpen() {
        CircuitBreakerConfig config = new ReadSourceProperties.Breaker().circuitBreakerConfig();

        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(ReadSourceProperties.WAIT_IN_OPEN_STATE.toMillis());
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
    }
}
