package org.danteplanner.backend.shared.gtid;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class GtidCookieTest {

    @Test
    void of_WithGtid_CarriesHttpOnlySecureSameSiteLaxValue() {
        String gtid = "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";

        ResponseCookie cookie = GtidCookie.of(gtid);

        assertThat(cookie.getName()).isEqualTo("ryw_gtid");
        assertThat(cookie.getValue()).isEqualTo(gtid);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void cleared_ForCaughtUp_HasMaxAgeZero() {
        ResponseCookie cookie = GtidCookie.cleared();

        assertThat(cookie.getName()).isEqualTo("ryw_gtid");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getValue()).isEmpty();
    }
}
