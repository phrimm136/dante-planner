package org.danteplanner.backend.shared.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigVersionValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(RateLimitProperties.class);

    @Test
    void configVersion_WhenPositive_Binds() {
        runner.withPropertyValues("rate-limit.config-version=3")
                .run(context -> assertThat(context.getBean(RateLimitProperties.class).getConfigVersion())
                        .isEqualTo(3));
    }

    @Test
    void configVersion_WhenZero_FailsStartup() {
        runner.withPropertyValues("rate-limit.config-version=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void configVersion_WhenAbsent_FailsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }
}
