package org.danteplanner.backend.shared.freshness;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessReadSettingsTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SettingsConfiguration.class);

    @Test
    void waitBound_WhenNoPropertyIsSet_Is250Milliseconds() {
        runner.run(context -> assertThat(context.getBean(FreshnessReadSettings.class).waitBound())
                .isEqualTo(Duration.ofMillis(250)));
    }

    @Test
    void waitBound_WhenSetToZero_FailsValidation() {
        runner.withPropertyValues("freshness.read.wait-bound=0ms").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("waitBound");
        });
    }

    @Configuration
    @EnableConfigurationProperties(FreshnessReadSettings.class)
    static class SettingsConfiguration {
    }
}
