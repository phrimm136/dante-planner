package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Binding.class);

    @Test
    void bind_WhenEverySettingIsMissing_ReportsEachOneAtOnce() {
        runner.run(context -> assertThat(context).getFailure().rootCause().message()
                .contains("JWT private key path is required. Set JWT_PRIVATE_KEY_PATH environment variable.")
                .contains("JWT public key path is required. Set JWT_PUBLIC_KEY_PATH environment variable.")
                .contains("JWT encryption key is required. Set JWT_ENCRYPTION_KEY environment variable."));
    }

    @Test
    void bind_WhenAnExpiryIsNotPositive_ReportsIt() {
        runner.withPropertyValues("jwt.private-key-path=a", "jwt.public-key-path=b", "jwt.encryption-key=c",
                        "jwt.access-token-expiry=0", "jwt.refresh-token-expiry=0")
                .run(context -> assertThat(context).getFailure().rootCause().message()
                        .contains("Access token expiry must be positive")
                        .contains("Refresh token expiry must be positive"));
    }

    @Test
    void bind_WhenEverySettingIsPresent_HoldsThem() {
        runner.withPropertyValues("jwt.private-key-path=a", "jwt.public-key-path=b", "jwt.encryption-key=c")
                .run(context -> assertThat(context.getBean(JwtSettings.class))
                        .isEqualTo(new JwtSettings("a", "b", "c", 900000L, 604800000L)));
    }

    @EnableConfigurationProperties(JwtSettings.class)
    static class Binding {
    }
}
