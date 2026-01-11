package org.danteplanner.backend.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClientIpResolver.
 *
 * <p>Tests trusted proxy validation and X-Forwarded-For parsing
 * to ensure rate limit bypass attacks are prevented.</p>
 */
class ClientIpResolverTest {

    private static final String TRUSTED_PROXY_IP = "10.0.0.1";
    private static final String UNTRUSTED_IP = "203.0.113.50";
    private static final String CLIENT_IP = "192.168.1.100";
    private static final String OTHER_CLIENT_IP = "192.168.1.200";

    private HttpServletRequest mockRequest(String remoteAddr, String xForwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        return request;
    }

    @Nested
    @DisplayName("Trusted Proxy Tests")
    class TrustedProxyTests {

        @Test
        @DisplayName("Should return X-Forwarded-For IP when request is from trusted proxy")
        void resolve_trustedProxy_returnsForwardedIp() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(TRUSTED_PROXY_IP, CLIENT_IP);

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result);
        }

        @Test
        @DisplayName("Should parse first IP from comma-separated X-Forwarded-For")
        void resolve_trustedProxy_multipleIps_returnsFirstIp() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            String multipleIps = CLIENT_IP + ", " + OTHER_CLIENT_IP + ", " + TRUSTED_PROXY_IP;
            HttpServletRequest request = mockRequest(TRUSTED_PROXY_IP, multipleIps);

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result, "Should return leftmost IP (original client)");
        }

        @Test
        @DisplayName("Should trim whitespace from X-Forwarded-For IP")
        void resolve_trustedProxy_trimWhitespace() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(TRUSTED_PROXY_IP, "  " + CLIENT_IP + "  ");

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result);
        }
    }

    @Nested
    @DisplayName("Untrusted Source Tests")
    class UntrustedSourceTests {

        @Test
        @DisplayName("Should verify that when a request coomes from an untrusted IP, the X-Forwarded-For header is completely ignored")
        void resolve_untrusted_ip_ignore_XForwarededFor() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(UNTRUSTED_IP, "fake-client-ip");
            
            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(UNTRUSTED_IP, result);
        }

        @Test
        @DisplayName("Should fallback to remoteAddr when no X-Forwarded-For header")
        void resolve_noHeader_returnsRemoteAddr() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(CLIENT_IP, null);

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result);
        }

        @Test
        @DisplayName("Should fallback to remoteAddr when X-Forwarded-For is blank")
        void resolve_blankHeader_returnsRemoteAddr() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(CLIENT_IP, "   ");

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty trusted proxy set")
        void resolve_emptyTrustedProxies_alwaysReturnsRemoteAddr() {
            // Arrange
            Set<String> trustedProxies = Collections.emptySet();
            HttpServletRequest request = mockRequest(CLIENT_IP, "spoofed-ip");

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result, "Should ignore header when no proxies trusted");
        }

        @Test
        @DisplayName("Should handle single IP in X-Forwarded-For (no comma)")
        void resolve_singleIpInHeader_returnsThatIp() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(TRUSTED_PROXY_IP, CLIENT_IP);

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(CLIENT_IP, result);
        }
    }

    @Nested
    @DisplayName("IP Validation Tests (Injection Prevention)")
    class IpValidationTests {

        @Test
        @DisplayName("Should reject malicious script in X-Forwarded-For")
        void resolve_maliciousScript_fallsBackToDirectIp() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(TRUSTED_PROXY_IP, "<script>alert(1)</script>");

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(TRUSTED_PROXY_IP, result, "Should reject invalid IP and use direct connection");
        }

        @Test
        @DisplayName("Should reject javascript: protocol in X-Forwarded-For")
        void resolve_javascriptProtocol_fallsBackToDirectIp() {
            // Arrange
            Set<String> trustedProxies = Set.of(TRUSTED_PROXY_IP);
            HttpServletRequest request = mockRequest(TRUSTED_PROXY_IP, "javascript:alert(1)");

            // Act
            String result = ClientIpResolver.resolve(request, trustedProxies);

            // Assert
            assertEquals(TRUSTED_PROXY_IP, result);
        }

        @Test
        @DisplayName("Should accept valid IPv4 address")
        void isValidIp_validIpv4_returnsTrue() {
            assertTrue(ClientIpResolver.isValidIp("192.168.1.100"));
            assertTrue(ClientIpResolver.isValidIp("10.0.0.1"));
            assertTrue(ClientIpResolver.isValidIp("255.255.255.255"));
            assertTrue(ClientIpResolver.isValidIp("0.0.0.0"));
        }

        @Test
        @DisplayName("Should accept valid IPv6 address")
        void isValidIp_validIpv6_returnsTrue() {
            assertTrue(ClientIpResolver.isValidIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
            assertTrue(ClientIpResolver.isValidIp("::1"));
            assertTrue(ClientIpResolver.isValidIp("::"));
        }

        @Test
        @DisplayName("Should reject invalid IP formats")
        void isValidIp_invalidFormats_returnsFalse() {
            assertFalse(ClientIpResolver.isValidIp("not-an-ip"));
            assertFalse(ClientIpResolver.isValidIp("192.168.1"));
            assertFalse(ClientIpResolver.isValidIp("192.168.1.256"));
            assertFalse(ClientIpResolver.isValidIp("<script>"));
            assertFalse(ClientIpResolver.isValidIp(""));
            assertFalse(ClientIpResolver.isValidIp(null));
        }
    }

    @Nested
    @DisplayName("Private IP Detection Tests (RFC 1918)")
    class PrivateIpDetectionTests {

        @Test
        @DisplayName("Should detect 10.x.x.x as private")
        void isPrivateIp_10Network_ReturnsTrue() {
            assertTrue(ClientIpResolver.isPrivateIp("10.0.0.1"));
            assertTrue(ClientIpResolver.isPrivateIp("10.255.255.255"));
            assertTrue(ClientIpResolver.isPrivateIp("10.123.45.67"));
        }

        @Test
        @DisplayName("Should detect 172.16-31.x.x as private")
        void isPrivateIp_172Network_ReturnsTrue() {
            assertTrue(ClientIpResolver.isPrivateIp("172.16.0.0"));
            assertTrue(ClientIpResolver.isPrivateIp("172.18.0.2"));
            assertTrue(ClientIpResolver.isPrivateIp("172.31.255.255"));
            assertTrue(ClientIpResolver.isPrivateIp("172.24.1.1"));
        }

        @Test
        @DisplayName("Should detect 192.168.x.x as private")
        void isPrivateIp_192Network_ReturnsTrue() {
            assertTrue(ClientIpResolver.isPrivateIp("192.168.0.1"));
            assertTrue(ClientIpResolver.isPrivateIp("192.168.1.100"));
            assertTrue(ClientIpResolver.isPrivateIp("192.168.255.255"));
        }

        @Test
        @DisplayName("Should detect 127.x.x.x as private (loopback)")
        void isPrivateIp_Loopback_ReturnsTrue() {
            assertTrue(ClientIpResolver.isPrivateIp("127.0.0.1"));
            assertTrue(ClientIpResolver.isPrivateIp("127.255.255.255"));
        }

        @Test
        @DisplayName("Should detect IPv6 localhost as private")
        void isPrivateIp_IPv6Localhost_ReturnsTrue() {
            assertTrue(ClientIpResolver.isPrivateIp("::1"));
            assertTrue(ClientIpResolver.isPrivateIp("::"));
        }

        @Test
        @DisplayName("Should reject 172.15.x.x as public (below range)")
        void isPrivateIp_172BelowRange_ReturnsFalse() {
            assertFalse(ClientIpResolver.isPrivateIp("172.15.255.255"));
            assertFalse(ClientIpResolver.isPrivateIp("172.0.0.1"));
        }

        @Test
        @DisplayName("Should reject 172.32.x.x as public (above range)")
        void isPrivateIp_172AboveRange_ReturnsFalse() {
            assertFalse(ClientIpResolver.isPrivateIp("172.32.0.0"));
            assertFalse(ClientIpResolver.isPrivateIp("172.255.255.255"));
        }

        @Test
        @DisplayName("Should reject public IPs")
        void isPrivateIp_PublicIps_ReturnsFalse() {
            assertFalse(ClientIpResolver.isPrivateIp("8.8.8.8"));
            assertFalse(ClientIpResolver.isPrivateIp("203.0.113.1"));
            assertFalse(ClientIpResolver.isPrivateIp("1.1.1.1"));
        }

        @Test
        @DisplayName("Should handle null and invalid IPs")
        void isPrivateIp_InvalidIps_ReturnsFalse() {
            assertFalse(ClientIpResolver.isPrivateIp(null));
            assertFalse(ClientIpResolver.isPrivateIp("not-an-ip"));
            assertFalse(ClientIpResolver.isPrivateIp(""));
        }
    }

    @Nested
    @DisplayName("Client Identifier Resolution Tests")
    class ClientIdentifierResolutionTests {

        private static final UUID TEST_DEVICE_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

        @Test
        @DisplayName("Should return ip:xxx for public IP")
        void resolveClientIdentifier_PublicIp_ReturnsIpFormat() {
            HttpServletRequest request = mockRequest("203.0.113.50", null);
            Set<String> trustedProxies = Collections.emptySet();

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertEquals("ip:203.0.113.50", result);
        }

        @Test
        @DisplayName("Should return device:xxx for private IP")
        void resolveClientIdentifier_PrivateIp_ReturnsDeviceFormat() {
            HttpServletRequest request = mockRequest("172.18.0.2", null);
            Set<String> trustedProxies = Collections.emptySet();

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertEquals("device:12345678-1234-1234-1234-123456789abc", result);
        }

        @Test
        @DisplayName("Should use CF-Connecting-IP if present and valid")
        void resolveClientIdentifier_CfHeader_UsesCfIp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("172.18.0.2");
            when(request.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.100");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);

            Set<String> trustedProxies = Collections.emptySet();

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertEquals("ip:203.0.113.100", result, "Should prefer CF-Connecting-IP");
        }

        @Test
        @DisplayName("Should fall back to X-Forwarded-For if CF header invalid")
        void resolveClientIdentifier_InvalidCfHeader_FallsBackToXff() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            when(request.getHeader("CF-Connecting-IP")).thenReturn("invalid-ip");
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");

            Set<String> trustedProxies = Set.of("10.0.0.1");

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertEquals("device:12345678-1234-1234-1234-123456789abc", result);
        }

        @Test
        @DisplayName("Should handle Docker NAT scenario (172.18.0.x)")
        void resolveClientIdentifier_DockerNat_ReturnsDeviceId() {
            HttpServletRequest request = mockRequest("172.18.0.2", null);
            Set<String> trustedProxies = Collections.emptySet();

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertTrue(result.startsWith("device:"), "Docker NAT IP should use device ID");
        }

        @Test
        @DisplayName("Should return device:unknown if device ID is null")
        void resolveClientIdentifier_NullDeviceId_ReturnsUnknown() {
            HttpServletRequest request = mockRequest("10.0.0.1", null);
            Set<String> trustedProxies = Collections.emptySet();

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, null);

            assertEquals("device:unknown", result);
        }

        @Test
        @DisplayName("Should handle trusted proxy with public X-Forwarded-For")
        void resolveClientIdentifier_TrustedProxyPublicIp_ReturnsIpFormat() {
            HttpServletRequest request = mockRequest("172.18.0.2", "203.0.113.200");
            Set<String> trustedProxies = Set.of("172.18.0.2");

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertEquals("ip:203.0.113.200", result, "Should use public IP from X-Forwarded-For");
        }

        @Test
        @DisplayName("Should handle trusted proxy with private X-Forwarded-For")
        void resolveClientIdentifier_TrustedProxyPrivateIp_ReturnsDeviceFormat() {
            HttpServletRequest request = mockRequest("127.0.0.1", "192.168.1.50");
            Set<String> trustedProxies = Set.of("127.0.0.1");

            String result = ClientIpResolver.resolveClientIdentifier(request, trustedProxies, TEST_DEVICE_ID);

            assertEquals("device:12345678-1234-1234-1234-123456789abc", result);
        }
    }
}
