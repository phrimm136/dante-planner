package org.danteplanner.backend.shared.config;

import java.util.UUID;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that one request yields one device id.
 *
 * <p>Several collaborators ask for the device id within a single request — the rate-limit seam
 * before the handler, the argument resolver during it. Minting per ask would set two cookies and
 * charge two buckets for one caller.</p>
 */
class DeviceIdResolverTest {

    private final DeviceIdResolver resolver = new DeviceIdResolver(new CookieUtils(false, "", "Lax"));

    @Test
    void resolve_WhenCookieAbsent_MintsOnceForTheWholeRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID first = resolver.resolve(request, response);
        UUID second = resolver.resolve(request, response);

        assertThat(first).isNotNull().isEqualTo(second);
        assertThat(response.getHeaders("Set-Cookie"))
                .filteredOn(header -> header.startsWith(CookieConstants.DEVICE_ID + "="))
                .hasSize(1);
    }

    @Test
    void resolve_WhenCookiePresent_ReadsItAndSetsNothing() {
        UUID existing = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieConstants.DEVICE_ID, existing.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolve(request, response)).isEqualTo(existing);
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void resolve_WhenCookieMalformed_MintsAReplacement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieConstants.DEVICE_ID, "not-a-uuid"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolve(request, response)).isNotNull();
        assertThat(response.getHeaders("Set-Cookie"))
                .filteredOn(header -> header.startsWith(CookieConstants.DEVICE_ID + "="))
                .hasSize(1);
    }
}
