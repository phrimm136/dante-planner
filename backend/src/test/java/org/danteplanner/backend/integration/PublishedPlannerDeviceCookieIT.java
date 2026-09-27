package org.danteplanner.backend.integration;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The anonymous detail read mints a device cookie only for a caller the edge cannot identify by
 * address.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PublishedPlannerDeviceCookieIT extends SharedMySqlContainerSupport {

    private static final String TRUSTED_PROXY_IP = "127.0.0.1";
    private static final String PUBLIC_CLIENT_IP = "203.0.113.7";
    private static final String PUBLIC_READ_BUCKET_SUFFIX = ":public-read";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private ProxyManager<byte[]> proxyManager;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "device-cookie-owner@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();
    }

    private static List<String> deviceIdCookies(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(CookieConstants.DEVICE_ID + "="))
                .toList();
    }

    private boolean bucketExists(String identifier) {
        byte[] key = (identifier + PUBLIC_READ_BUCKET_SUFFIX).getBytes(StandardCharsets.UTF_8);
        return proxyManager.getProxyConfiguration(key).isPresent();
    }

    @Test
    void publishedDetail_WhenPublicCallerArrivesThroughTheEdge_SetsNoDeviceIdCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/planner/md/published/{id}", plannerId)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_IP);
                            return request;
                        })
                        .header("CF-Connecting-IP", PUBLIC_CLIENT_IP))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(deviceIdCookies(response)).isEmpty();
        assertThat(bucketExists("ip:" + PUBLIC_CLIENT_IP)).isTrue();
    }

    @Test
    void publishedDetail_WhenLoopbackPeerHasNoCookie_MintsOneAndChargesItsDeviceBucket() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/planner/md/published/{id}", plannerId)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_IP);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        List<String> cookies = deviceIdCookies(response);
        assertThat(cookies).hasSize(1);
        String minted = cookies.get(0).substring((CookieConstants.DEVICE_ID + "=").length()).split(";", 2)[0];
        assertThat(bucketExists("device:" + UUID.fromString(minted))).isTrue();
    }
}
