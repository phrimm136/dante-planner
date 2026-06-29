package org.danteplanner.backend.config;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("getUrl returns the first allowed origin")
    void getUrl_returnsFirstOrigin() {
        assertThat(properties.getUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("resolveReturnTo keeps a returnTo whose origin is allowlisted")
    void resolveReturnTo_whenOriginAllowed_keepsIt() {
        assertThat(properties.resolveReturnTo("http://localhost:5173/planner/1"))
                .isEqualTo("http://localhost:5173/planner/1");
        assertThat(properties.resolveReturnTo("http://localhost/settings"))
                .isEqualTo("http://localhost/settings");
    }

    @Test
    @DisplayName("resolveReturnTo rejects a foreign origin (open-redirect guard)")
    void resolveReturnTo_whenForeignOrigin_fallsBack() {
        assertThat(properties.resolveReturnTo("http://evil.com/phish"))
                .isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("resolveReturnTo rejects a port mismatch (exact origin match)")
    void resolveReturnTo_whenPortMismatch_fallsBack() {
        assertThat(properties.resolveReturnTo("http://localhost:9999/x"))
                .isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("resolveReturnTo rejects protocol-relative, relative, malformed, and blank values")
    void resolveReturnTo_whenNotAbsoluteAllowed_fallsBack() {
        assertThat(properties.resolveReturnTo("//evil.com")).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo("/planner/1")).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo("not a url")).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo(null)).isEqualTo("http://localhost:5173");
        assertThat(properties.resolveReturnTo("   ")).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("resolveReturnTo rejects classic open-redirect bypass attempts")
    void resolveReturnTo_whenBypassAttempt_fallsBack() {
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
    @DisplayName("constructor fails fast when no origin is configured")
    void constructor_whenBlank_throws() {
        assertThatThrownBy(() -> new FrontendProperties("   "))
                .isInstanceOf(IllegalStateException.class);
    }
}
