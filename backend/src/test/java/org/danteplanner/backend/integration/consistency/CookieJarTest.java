package org.danteplanner.backend.integration.consistency;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.support.AuthCookies;

import static org.assertj.core.api.Assertions.assertThat;

class CookieJarTest {

    private static final String GTID = UUID.randomUUID() + ":1-7";

    @Test
    void jar_WhenACookieIsSetThenClearedWithMaxAgeZero_DropsIt() {
        CookieJar jar = new CookieJar(AuthCookies.freshDeviceId());

        jar.apply(List.of(GtidCookie.of(GTID).toString()));
        jar.apply(List.of(GtidCookie.cleared().toString()));

        assertThat(jar.cookies()).extracting(Cookie::getName).doesNotContain(GtidCookie.NAME);
    }

    @Test
    void jar_WhenACookieIsSecure_StillSendsIt() {
        CookieJar jar = new CookieJar(AuthCookies.freshDeviceId());
        String header = GtidCookie.of(GTID).toString();

        jar.apply(List.of(header));

        assertThat(header).contains("Secure");
        assertThat(jar.cookies())
                .filteredOn(cookie -> cookie.getName().equals(GtidCookie.NAME))
                .extracting(Cookie::getValue)
                .containsExactly(GtidCookie.of(GTID).getValue());
    }
}
