package org.danteplanner.backend.shared.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigVersionValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(Binding.class);

    @EnableConfigurationProperties(RateLimitProperties.class)
    static class Binding {
    }

    private static final String[] BUCKETS = Stream.of("crud", "sse", "auth", "comment", "report", "moderation",
                    "public-read", "planner-stats", "view-record")
            .map(bucket -> "rate-limit." + bucket)
            .flatMap(bucket -> Stream.of(
                    bucket + ".capacity=1", bucket + ".refill-tokens=1", bucket + ".refill-duration-seconds=1"))
            .toArray(String[]::new);

    @Test
    void bucket_WhenMissing_FailsStartup() {
        runner.withPropertyValues("rate-limit.config-version=3")
                .withPropertyValues(Arrays.copyOf(BUCKETS, BUCKETS.length - 3))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"capacity", "refill-tokens", "refill-duration-seconds"})
    void bucketComponent_WhenZero_FailsStartupNamingIt(String component) {
        runner.withPropertyValues(BUCKETS)
                .withPropertyValues("rate-limit.config-version=3", "rate-limit.view-record." + component + "=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("viewRecord");
                });
    }

    @Test
    void configVersion_WhenPositive_Binds() {
        runner.withPropertyValues(BUCKETS).withPropertyValues("rate-limit.config-version=3")
                .run(context -> assertThat(context.getBean(RateLimitProperties.class).configVersion())
                        .isEqualTo(3));
    }

    @Test
    void configVersion_WhenZero_FailsStartup() {
        runner.withPropertyValues(BUCKETS).withPropertyValues("rate-limit.config-version=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void configVersion_WhenAbsent_FailsStartup() {
        runner.withPropertyValues(BUCKETS).run(context -> assertThat(context).hasFailed());
    }
}
