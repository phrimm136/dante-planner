package org.danteplanner.backend.config;
import org.danteplanner.backend.shared.config.FrontendProperties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FrontendProperties}, focused on the open-redirect guard in
 * {@link FrontendProperties#resolveReturnTo} — a {@code returnTo} is honored only when its origin
 * exactly matches an allowed origin; everything else falls back to the default.
 */
class FrontendPropertiesTest {

    private final FrontendProperties properties =
            new FrontendProperties("http://localhost:5173,http://localhost");

    @Test
    void getUrl_WhenCalled_ReturnsFirstOrigin() {
        assertThat(properties.getUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    void resolveReturnTo_WhenOriginAllowed_KeepsIt() {
        assertThat(properties.resolveReturnTo("http://localhost:5173/planner/1"))
                .isEqualTo("http://localhost:5173/planner/1");
        assertThat(properties.resolveReturnTo("http://localhost/settings"))
                .isEqualTo("http://localhost/settings");
    }

    @Test
    void resolveReturnTo_WhenForeignOrigin_FallsBack() {
        assertThat(properties.resolveReturnTo("http://evil.com/phish"))
                .isEqualTo("http://localhost:5173");
    }

    @Test
    void resolveReturnTo_WhenPortMismatch_FallsBack() {
        assertThat(properties.resolveReturnTo("http://localhost:9999/x"))
                .isEqualTo("http://localhost:5173");
    }

    @Test
    void resolveReturnTo_WhenNotAbsoluteAllowed_FallsBack() {
        assertThat(properties.resolveReturnTo("//evil.com")).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo("/planner/1")).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo("not a url")).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo(null)).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo("   ")).isEqualTo("http://localhost:5173");
    }

    @Test
    void resolveReturnTo_WhenBypassAttempt_FallsBack() {
        // javascript: scheme has a null host
        assertThat(properties.resolveReturnTo("javascript:alert(1)")).isEqualTo("http://localhost:5173");
        // userinfo-@ trick: the real host is evil.com, not the allowlisted prefix
        assertThat(properties.resolveReturnTo("http://localhost:5173@evil.com/")).isEqualTo("http://localhost:5173");
        // host-suffix trick: localhost.evil.com is a different origin
        assertThat(properties.resolveReturnTo("http://localhost.evil.com/")).isEqualTo("http://localhost:5173");
        // scheme mismatch against an http-only allowlist
        assertThat(properties.resolveReturnTo("https://localhost:5173/")).isEqualTo("http://localhost:5173");
        // backslash injection — invalid authority, URI.create throws → default
        assertThat(properties.resolveReturnTo("http://localhost:5173\\@evil.com")).isEqualTo("http://localhost:5173");
        // non-numeric port suffix — invalid authority → default
        assertThat(properties.resolveReturnTo("http://localhost:5173.evil.com/")).isEqualTo("http://localhost:5173");
    }

    @Test
    void constructor_WhenBlank_Throws() {
        assertThatThrownBy(() -> new FrontendProperties("   "))
                .isInstanceOf(IllegalStateException.class);
    }
}
