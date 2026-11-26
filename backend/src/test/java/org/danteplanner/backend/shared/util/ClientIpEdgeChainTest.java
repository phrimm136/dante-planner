package org.danteplanner.backend.shared.util;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import jakarta.servlet.ServletException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpEdgeChainTest {

    private static final String CHART_TRUSTED_PROXIES = "127.0.0.1/32,::1/128,10.42.0.0/16";
    private static final String CLIENT_IP = "8.8.8.8";
    private static final UUID DEVICE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static String throughEdge(String peer, String xForwardedFor) throws IOException, ServletException {
        return throughEdge(CHART_TRUSTED_PROXIES, peer, xForwardedFor);
    }

    private static String throughEdge(String trustedProxies, String peer, String xForwardedFor)
            throws IOException, ServletException {
        SecurityProperties properties = new SecurityProperties();
        properties.setTrustedProxyIps(trustedProxies);
        properties.parseTrustedProxyIps();

        AtomicReference<String> identifier = new AtomicReference<>();
        RemoteIpValve valve = new RemoteIpValve();
        valve.setInternalProxies(trustedProxies);
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                identifier.set(ClientIpResolver.resolveClientIdentifier(request, properties, () -> DEVICE_ID));
            }
        });

        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        coyoteRequest.getMimeHeaders().addValue("X-Forwarded-For").setString(xForwardedFor);
        coyoteRequest.getMimeHeaders().addValue("CF-Connecting-IP").setString(CLIENT_IP);
        Request request = new Request(new Connector(), coyoteRequest);
        request.setRemoteAddr(peer);
        request.setRemoteHost(peer);
        valve.invoke(request, null);
        return identifier.get();
    }

    @Test
    void resolveClientIdentifier_WhenHostNetworkTraefikForwardsCloudflared_KeysOnTheCloudflareHeader()
            throws Exception {
        assertThat(throughEdge("10.42.1.0", "127.0.0.1")).isEqualTo("ip:" + CLIENT_IP);
    }

    @Test
    void resolveClientIdentifier_WhenHostNetworkTraefikForwardsCloudflaredOnIpv6Loopback_KeysOnTheCloudflareHeader()
            throws Exception {
        assertThat(throughEdge("10.42.1.0", "::1")).isEqualTo("ip:" + CLIENT_IP);
    }

    @Test
    void resolveClientIdentifier_WhenTheIpv6LoopbackHopIsWrittenInFull_KeysOnTheCloudflareHeader() throws Exception {
        assertThat(throughEdge("10.42.1.0", "0:0:0:0:0:0:0:1")).isEqualTo("ip:" + CLIENT_IP);
    }

    @Test
    void resolveClientIdentifier_WhenTraefikPodForwardsCloudflaredPod_KeysOnTheCloudflareHeader() throws Exception {
        assertThat(throughEdge("10.42.3.7", "10.42.5.9")).isEqualTo("ip:" + CLIENT_IP);
    }

    @Test
    void resolveClientIdentifier_WhenVpcNodeClaimsLoopback_IgnoresTheCloudflareHeader() throws Exception {
        assertThat(throughEdge("10.20.0.5", "127.0.0.1")).isEqualTo("device:" + DEVICE_ID);
    }

    @Test
    void resolveClientIdentifier_WhenTrustedPodForwardsAVpcNode_IgnoresTheCloudflareHeader() throws Exception {
        assertThat(throughEdge("10.42.3.7", "10.20.0.5")).isEqualTo("device:" + DEVICE_ID);
    }

    @Test
    void resolveClientIdentifier_WhenPublicPeerClaimsLoopback_KeysOnThePeer() throws Exception {
        assertThat(throughEdge("203.0.113.50", "127.0.0.1")).isEqualTo("ip:203.0.113.50");
    }

    @Test
    void resolveClientIdentifier_WhenNoProxyIsTrusted_KeysThePrivatePeerByDevice() throws Exception {
        assertThat(throughEdge("", "172.18.0.5", CLIENT_IP)).isEqualTo("device:" + DEVICE_ID);
    }

    @Test
    void applicationProperties_WhenResolved_GiveTheValveTheTrustedProxyList() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(
                new ResourcePropertySource(new ClassPathResource("application.properties")));

        assertThat(environment.getProperty("server.tomcat.remoteip.internal-proxies"))
                .isEqualTo(environment.getProperty("security.trusted-proxy-ips"))
                .isEqualTo("127.0.0.1/32,::1/128");
    }
}
