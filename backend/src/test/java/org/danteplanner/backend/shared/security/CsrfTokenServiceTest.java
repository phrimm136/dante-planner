package org.danteplanner.backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.security.CsrfTokenService.TokenOutcome;

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
    void parse_WhenTheTokenIsMintedAtMidnightOnMarchFirst_AcceptsSevenDaysAndOneMinuteOfSkew(
            Instant presentedAt, boolean valid) {
        String token = at(Instant.parse("2026-03-01T00:00:00Z")).mint();

        assertThat(at(presentedAt).parse(token) instanceof TokenOutcome.ServerIssued).isEqualTo(valid);
    }

    @ParameterizedTest
    @CsvSource({"nonce.1772323200000, true", "noTimestamp, false", "nonce.notANumber, false", "nonce., false"})
    void parse_WhenTheMacIsGenuine_AcceptsOnlyAPayloadWithATimestamp(String payload, boolean valid) throws Exception {
        byte[] mac = hmac(hmac(new byte[32], "danteplanner/csrf-token/v1\u0001"), payload);
        String token = payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac);

        assertThat(at(Instant.parse("2026-03-01T00:00:00Z")).parse(token) instanceof TokenOutcome.ServerIssued).isEqualTo(valid);
    }

    private static byte[] hmac(byte[] key, String text) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
    }

    private static CsrfTokenService at(Instant now) {
        return new CsrfTokenService(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }
}
