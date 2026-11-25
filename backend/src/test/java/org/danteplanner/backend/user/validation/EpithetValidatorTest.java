package org.danteplanner.backend.user.validation;

import java.time.Clock;

import org.danteplanner.backend.shared.config.EpithetConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class EpithetValidatorTest {

    private final EpithetValidator validator = new EpithetValidator(new EpithetConfig(Clock.systemUTC()));

    @Test
    void requireValidEpithet_WhenUnknown_ThrowsInvalidEpithet() {
        assertRejected(() -> validator.requireValidEpithet("NOBODY"))
                .withCode("INVALID_EPITHET")
                .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Invalid epithet: NOBODY"));
    }

    @Test
    void requireValidEpithet_WhenKnown_CarriesTheEpithet() {
        assertThat(validator.requireValidEpithet("NAIVE").getValue()).isEqualTo("NAIVE");
    }
}
