package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class GtidCookieTest {

    @Test
    void of_WhenGtid_CarriesHttpOnlySecureSameSiteLaxRoundTrippableValue() {
        String gtid = "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";

        ResponseCookie cookie = GtidCookie.of(gtid);

        assertThat(cookie.getName()).isEqualTo("ryw_gtid");
        assertThat(GtidCookie.decode(cookie.getValue())).contains(gtid);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void of_WhenMultiUuidGtidSet_ProducesCookieSafeValue() {
        String gtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-100,"
                + "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50";

        ResponseCookie cookie = GtidCookie.of(gtidSet);

        assertThat(cookie.getValue()).doesNotContain(",", ";", "\"", "\\", " ");
        assertThat(GtidCookie.decode(cookie.getValue())).contains(gtidSet);
    }

    @Test
    void decode_WhenBlankValue_ReturnsEmpty() {
        assertThat(GtidCookie.decode("")).isEmpty();
    }

    @Test
    void decode_WhenNonBase64Value_ReturnsEmpty() {
        assertThat(GtidCookie.decode("3e11fa47-71ca-11e1-9e33-c80aa9429562:1-5,tampered")).isEmpty();
    }

    @Test
    void cleared_WhenCaughtUp_HasMaxAgeZero() {
        ResponseCookie cookie = GtidCookie.cleared();

        assertThat(cookie.getName()).isEqualTo("ryw_gtid");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getValue()).isEmpty();
    }

    @Test
    void decode_WhenNotGtidSyntax_ReturnsEmptyAndIsUnusable() {
        String cookieValue = GtidCookie.of("not-a-gtid").getValue();

        assertThat(GtidCookie.decode(cookieValue)).isEmpty();
        assertThat(GtidCookie.isUnusable(cookieValue)).isTrue();
    }

    @Test
    void decode_WhenIntervalReversed_ReturnsEmpty() {
        assertThat(GtidCookie.decode(GtidCookie.of("3e11fa47-71ca-11e1-9e33-c80aa9429562:5-1").getValue()))
                .isEmpty();
    }

    @Test
    void decode_WhenIntervalStartsAtZero_ReturnsEmpty() {
        assertThat(GtidCookie.decode(GtidCookie.of("3e11fa47-71ca-11e1-9e33-c80aa9429562:0-5").getValue()))
                .isEmpty();
    }

    @Test
    void decode_WhenIntervalOverflowsLong_ReturnsEmpty() {
        assertThat(GtidCookie.decode(
                GtidCookie.of("3e11fa47-71ca-11e1-9e33-c80aa9429562:1-99999999999999999999").getValue()))
                .isEmpty();
    }

    @Test
    void decode_WhenUuidMalformed_ReturnsEmpty() {
        assertThat(GtidCookie.decode(GtidCookie.of("3e11fa47-71ca-11e1-9e33-c80aa942956:1-5").getValue()))
                .isEmpty();
    }

    @Test
    void decode_WhenIntervalMissing_ReturnsEmpty() {
        assertThat(GtidCookie.decode(GtidCookie.of("3e11fa47-71ca-11e1-9e33-c80aa9429562:").getValue()))
                .isEmpty();
    }

    @Test
    void decode_WhenTrailingComma_ReturnsEmpty() {
        assertThat(GtidCookie.decode(GtidCookie.of("3e11fa47-71ca-11e1-9e33-c80aa9429562:1-5,").getValue()))
                .isEmpty();
    }

    @Test
    void decode_WhenUppercaseUuidSingleIntervalAndEqualBounds_ReturnsSet() {
        String gtid = "3E11FA47-71CA-11E1-9E33-C80AA9429562:7:9-9";

        assertThat(GtidCookie.decode(GtidCookie.of(gtid).getValue())).contains(gtid);
        assertThat(GtidCookie.isUnusable(GtidCookie.of(gtid).getValue())).isFalse();
    }

    @Test
    void decode_WhenMultipleIntervalsAndWhitespaceAroundCommas_ReturnsSet() {
        String gtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-3:11:47-49 ,\n"
                + "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50";

        assertThat(GtidCookie.decode(GtidCookie.of(gtidSet).getValue())).contains(gtidSet);
    }

    @Test
    void isUnusable_WhenAbsentBlankOrNonBase64_ReturnsFalse() {
        assertThat(GtidCookie.isUnusable(null)).isFalse();
        assertThat(GtidCookie.isUnusable("")).isFalse();
        assertThat(GtidCookie.isUnusable("not*base64url*value")).isFalse();
    }
}
