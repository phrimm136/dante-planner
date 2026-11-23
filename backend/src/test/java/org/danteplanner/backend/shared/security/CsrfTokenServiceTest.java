package org.danteplanner.backend.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.danteplanner.backend.shared.config.JwtProperties;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfTokenServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties(null, null, new byte[32], 900000L, 604800000L);

    @ParameterizedTest
    @CsvSource({
            "2026-03-01T00:00:00Z, true",
            "2026-03-08T00:00:00Z, true",
            "2026-03-08T00:00:00.001Z, false",
            "2026-02-28T23:59:00Z, true",
            "2026-02-28T23:58:59.999Z, false"})
    void isValid_WhenTheTokenIsMintedAtMidnightOnMarchFirst_AcceptsSevenDaysAndOneMinuteOfSkew(
            Instant presentedAt, boolean valid) {
        String token = at(Instant.parse("2026-03-01T00:00:00Z")).mint();

        assertThat(at(presentedAt).isValid(token)).isEqualTo(valid);
    }

    private static CsrfTokenService at(Instant now) {
        return new CsrfTokenService(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }
}
