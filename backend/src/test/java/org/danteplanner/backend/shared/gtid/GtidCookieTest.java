package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class GtidCookieTest {

    @Test
    void of_WithGtid_CarriesHttpOnlySecureSameSiteLaxRoundTrippableValue() {
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
    void of_WithMultiUuidGtidSet_ProducesCookieSafeValue() {
        String gtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-100,"
                + "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f:1-50";

        ResponseCookie cookie = GtidCookie.of(gtidSet);

        assertThat(cookie.getValue()).doesNotContain(",", ";", "\"", "\\", " ");
        assertThat(GtidCookie.decode(cookie.getValue())).contains(gtidSet);
    }

    @Test
    void decode_WithBlankValue_ReturnsEmpty() {
        assertThat(GtidCookie.decode("")).isEmpty();
    }

    @Test
    void decode_WithNonBase64Value_ReturnsEmpty() {
        assertThat(GtidCookie.decode("3e11fa47-71ca-11e1-9e33-c80aa9429562:1-5,tampered")).isEmpty();
    }

    @Test
    void cleared_ForCaughtUp_HasMaxAgeZero() {
        ResponseCookie cookie = GtidCookie.cleared();

        assertThat(cookie.getName()).isEqualTo("ryw_gtid");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getValue()).isEmpty();
    }
}
