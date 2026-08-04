package org.danteplanner.backend.shared.util;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers trusted-proxy admission, header parsing, and the private-address fallback that keeps
 * every caller behind a NAT from sharing one rate-limit bucket.
 */
class ClientIpResolverTest {

    private static final String TRUSTED_PROXY_IP = "10.0.0.1";
    private static final String PROXY_IN_CIDR = "172.18.0.7";
    private static final String UNTRUSTED_IP = "203.0.113.50";
    private static final String PUBLIC_CLIENT_IP = "8.8.8.8";
    private static final String OTHER_PUBLIC_IP = "1.1.1.1";
    private static final String PRIVATE_CLIENT_IP = "192.168.1.100";
    private static final UUID DEVICE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static SecurityProperties properties(String trustedProxyIps) {
        SecurityProperties properties = new SecurityProperties();
        properties.setTrustedProxyIps(trustedProxyIps);
        properties.parseTrustedProxyIps();
        return properties;
    }

    private static HttpServletRequest request(String remoteAddr, String xForwardedFor) {
        return request(remoteAddr, xForwardedFor, null);
    }

    private static HttpServletRequest request(String remoteAddr, String xForwardedFor, String cfConnectingIp) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        lenientHeader(request, "X-Forwarded-For", xForwardedFor);
        lenientHeader(request, "CF-Connecting-IP", cfConnectingIp);
        return request;
    }

    private static void lenientHeader(HttpServletRequest request, String name, String value) {
        when(request.getHeader(name)).thenReturn(value);
    }

    @Nested
    class Resolve {

        @Test
        void resolve_WhenPeerIsTrustedByExactMatch_ReturnsForwardedIp() {
            String result = ClientIpResolver.resolve(
                    request(TRUSTED_PROXY_IP, PUBLIC_CLIENT_IP), properties(TRUSTED_PROXY_IP));

            assertThat(result).isEqualTo(PUBLIC_CLIENT_IP);
        }

        @Test
        void resolve_WhenPeerIsTrustedByCidrRange_ReturnsForwardedIp() {
            String result = ClientIpResolver.resolve(
                    request(PROXY_IN_CIDR, PUBLIC_CLIENT_IP), properties("172.18.0.0/16"));

            assertThat(result).isEqualTo(PUBLIC_CLIENT_IP);
        }

        @Test
        void resolve_WhenPeerIsOutsideEveryCidrRange_IgnoresTheHeader() {
            String result = ClientIpResolver.resolve(
                    request(UNTRUSTED_IP, PUBLIC_CLIENT_IP), properties("172.18.0.0/16"));

            assertThat(result).isEqualTo(UNTRUSTED_IP);
        }

        @Test
        void resolve_WhenHeaderCarriesAChainOfHops_ReturnsTheLeftmost() {
            String chain = PUBLIC_CLIENT_IP + ", " + OTHER_PUBLIC_IP + ", " + TRUSTED_PROXY_IP;

            String result = ClientIpResolver.resolve(
                    request(TRUSTED_PROXY_IP, chain), properties(TRUSTED_PROXY_IP));

            assertThat(result).isEqualTo(PUBLIC_CLIENT_IP);
        }

        @Test
        void resolve_WhenPeerIsUntrusted_IgnoresTheHeader() {
            String result = ClientIpResolver.resolve(
                    request(UNTRUSTED_IP, PUBLIC_CLIENT_IP), properties(TRUSTED_PROXY_IP));

            assertThat(result).isEqualTo(UNTRUSTED_IP);
        }

        @Test
        void resolve_WhenHeaderIsAbsent_ReturnsTheDirectPeer() {
            String result = ClientIpResolver.resolve(
                    request(TRUSTED_PROXY_IP, null), properties(TRUSTED_PROXY_IP));

            assertThat(result).isEqualTo(TRUSTED_PROXY_IP);
        }

        @Test
        void resolve_WhenHeaderIsBlank_ReturnsTheDirectPeer() {
            String result = ClientIpResolver.resolve(
                    request(TRUSTED_PROXY_IP, "   "), properties(TRUSTED_PROXY_IP));

            assertThat(result).isEqualTo(TRUSTED_PROXY_IP);
        }

        @Test
        void resolve_WhenHeaderIsNotAnAddress_ReturnsTheDirectPeer() {
            String result = ClientIpResolver.resolve(
                    request(TRUSTED_PROXY_IP, "<script>alert(1)</script>"), properties(TRUSTED_PROXY_IP));

            assertThat(result).isEqualTo(TRUSTED_PROXY_IP);
        }

        @Test
        void resolve_WhenNoProxyIsConfigured_IgnoresTheHeader() {
            String result = ClientIpResolver.resolve(
                    request(TRUSTED_PROXY_IP, PUBLIC_CLIENT_IP), properties(""));

            assertThat(result).isEqualTo(TRUSTED_PROXY_IP);
        }
    }

    @Nested
    class ValidIp {

        @Test
        void isValidIp_WhenIpv4_ReturnsTrue() {
            assertThat(ClientIpResolver.isValidIp("192.168.1.100")).isTrue();
            assertThat(ClientIpResolver.isValidIp("255.255.255.255")).isTrue();
            assertThat(ClientIpResolver.isValidIp("0.0.0.0")).isTrue();
        }

        @Test
        void isValidIp_WhenIpv6_ReturnsTrue() {
            assertThat(ClientIpResolver.isValidIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334")).isTrue();
            assertThat(ClientIpResolver.isValidIp("::1")).isTrue();
            assertThat(ClientIpResolver.isValidIp("::")).isTrue();
        }

        @Test
        void isValidIp_WhenMalformed_ReturnsFalse() {
            assertThat(ClientIpResolver.isValidIp("not-an-ip")).isFalse();
            assertThat(ClientIpResolver.isValidIp("192.168.1")).isFalse();
            assertThat(ClientIpResolver.isValidIp("192.168.1.256")).isFalse();
            assertThat(ClientIpResolver.isValidIp("<script>")).isFalse();
            assertThat(ClientIpResolver.isValidIp("")).isFalse();
            assertThat(ClientIpResolver.isValidIp(null)).isFalse();
        }
    }

    @Nested
    class PrivateIp {

        @Test
        void isPrivateIp_WhenInsideRfc1918_ReturnsTrue() {
            assertThat(ClientIpResolver.isPrivateIp("10.0.0.1")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("10.255.255.255")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("172.16.0.0")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("172.18.0.2")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("172.31.255.255")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("192.168.0.1")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("192.168.255.255")).isTrue();
        }

        @Test
        void isPrivateIp_WhenLoopback_ReturnsTrue() {
            assertThat(ClientIpResolver.isPrivateIp("127.0.0.1")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("127.255.255.255")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("::1")).isTrue();
            assertThat(ClientIpResolver.isPrivateIp("::")).isTrue();
        }

        @Test
        void isPrivateIp_WhenJustOutsideThe172Block_ReturnsFalse() {
            assertThat(ClientIpResolver.isPrivateIp("172.15.255.255")).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("172.0.0.1")).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("172.32.0.0")).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("172.255.255.255")).isFalse();
        }

        @Test
        void isPrivateIp_WhenPublicOrMalformed_ReturnsFalse() {
            assertThat(ClientIpResolver.isPrivateIp(PUBLIC_CLIENT_IP)).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("203.0.113.1")).isFalse();
            assertThat(ClientIpResolver.isPrivateIp(OTHER_PUBLIC_IP)).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334")).isFalse();
            assertThat(ClientIpResolver.isPrivateIp(null)).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("not-an-ip")).isFalse();
            assertThat(ClientIpResolver.isPrivateIp("")).isFalse();
        }
    }

    @Nested
    class ClientIdentifier {

        @Test
        void resolveClientIdentifier_WhenPublicIpBehindTrustedProxy_KeysOnTheIp() {
            String result = ClientIpResolver.resolveClientIdentifier(
                    request(TRUSTED_PROXY_IP, PUBLIC_CLIENT_IP), properties(TRUSTED_PROXY_IP), DEVICE_ID);

            assertThat(result).isEqualTo("ip:" + PUBLIC_CLIENT_IP);
        }

        @Test
        void resolveClientIdentifier_WhenProxyIsTrustedByCidrRange_KeysOnTheForwardedIp() {
            String result = ClientIpResolver.resolveClientIdentifier(
                    request(PROXY_IN_CIDR, PUBLIC_CLIENT_IP), properties("172.18.0.0/16"), DEVICE_ID);

            assertThat(result).isEqualTo("ip:" + PUBLIC_CLIENT_IP);
        }

        @Test
        void resolveClientIdentifier_WhenCloudflareHeaderArrivesFromTrustedProxy_KeysOnIt() {
            String result = ClientIpResolver.resolveClientIdentifier(
                    request(PROXY_IN_CIDR, OTHER_PUBLIC_IP, PUBLIC_CLIENT_IP),
                    properties("172.18.0.0/16"), DEVICE_ID);

            assertThat(result).isEqualTo("ip:" + PUBLIC_CLIENT_IP);
        }

        @Test
        void resolveClientIdentifier_WhenCloudflareHeaderArrivesFromUntrustedPeer_IgnoresIt() {
            String result = ClientIpResolver.resolveClientIdentifier(
                    request(UNTRUSTED_IP, null, PUBLIC_CLIENT_IP), properties("172.18.0.0/16"), DEVICE_ID);

            assertThat(result).isEqualTo("ip:" + UNTRUSTED_IP);
        }

        @Test
        void resolveClientIdentifier_WhenResolvedIpIsPrivate_FallsBackToTheDeviceId() {
            String result = ClientIpResolver.resolveClientIdentifier(
                    request(PROXY_IN_CIDR, PRIVATE_CLIENT_IP), properties("172.18.0.0/16"), DEVICE_ID);

            assertThat(result).isEqualTo("device:" + DEVICE_ID);
        }

        @Test
        void resolveClientIdentifier_WhenResolvedIpIsPrivateAndNoDeviceId_FallsBackToUnknown() {
            String result = ClientIpResolver.resolveClientIdentifier(
                    request(PROXY_IN_CIDR, PRIVATE_CLIENT_IP), properties("172.18.0.0/16"), null);

            assertThat(result).isEqualTo("device:unknown");
        }
    }
}
