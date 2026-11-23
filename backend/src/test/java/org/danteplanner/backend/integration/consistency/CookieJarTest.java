package org.danteplanner.backend.integration.consistency;

import java.time.Duration;
import java.util.List;

import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.support.AuthCookies;

import static org.assertj.core.api.Assertions.assertThat;

class CookieJarTest {

    private static final String NAME = "jar-cookie";
    private static final String VALUE = "jar-value";

    @Test
    void jar_WhenACookieIsSetThenClearedWithMaxAgeZero_DropsIt() {
        CookieJar jar = new CookieJar(AuthCookies.freshDeviceId());

        jar.apply(List.of(secure(VALUE).build().toString()));
        jar.apply(List.of(secure("").maxAge(Duration.ZERO).build().toString()));

        assertThat(jar.cookies()).extracting(Cookie::getName).doesNotContain(NAME);
    }

    @Test
    void jar_WhenACookieIsSecure_StillSendsIt() {
        CookieJar jar = new CookieJar(AuthCookies.freshDeviceId());
        String header = secure(VALUE).build().toString();

        jar.apply(List.of(header));

        assertThat(header).contains("Secure");
        assertThat(jar.cookies())
                .filteredOn(cookie -> cookie.getName().equals(NAME))
                .extracting(Cookie::getValue)
                .containsExactly(VALUE);
    }

    private static ResponseCookie.ResponseCookieBuilder secure(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Lax").path("/");
    }
}
