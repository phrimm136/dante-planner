package org.danteplanner.backend.ratelimit;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins which bucket the anonymous detail read charges and which identity its view carries, for a
 * caller keyed by address and for one keyed by device.
 */
class PublishedPlannerClientBucketTest {

    private static final String TRUSTED_PROXY_IP = "127.0.0.1";
    private static final String PUBLIC_CLIENT_IP = "203.0.113.7";
    private static final UUID PLANNER_ID = UUID.fromString("0b7e8f3a-1c2d-4e5f-8a9b-0c1d2e3f4a5b");

    private RateLimitService rateLimitService;
    private PublishedPlannerQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        queryService = mock(PublishedPlannerQueryService.class);
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setTrustedProxyIps(TRUSTED_PROXY_IP);
        securityProperties.parseTrustedProxyIps();
        DeviceIdResolver deviceIdResolver = new DeviceIdResolver(new CookieUtils(false, "", "Lax"));

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimitService,
                securityProperties,
                deviceIdResolver,
                new FrontendProperties("https://planner.example"),
                new ProblemWriter(objectMapper));
        PublishedPlannerController controller = new PublishedPlannerController(
                queryService, securityProperties, new ByIdReadGuard(), deviceIdResolver);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor)
                .build();
    }

    private MockHttpServletResponse readDetail(String cfConnectingIp) throws Exception {
        return mockMvc.perform(get("/api/planner/md/published/{id}", PLANNER_ID)
                        .with(request -> {
                            request.setRemoteAddr(TRUSTED_PROXY_IP);
                            if (cfConnectingIp != null) {
                                request.addHeader("CF-Connecting-IP", cfConnectingIp);
                            }
                            return request;
                        }))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private static List<String> deviceIdCookies(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(CookieConstants.DEVICE_ID + "="))
                .toList();
    }

    @Test
    void publishedDetail_WhenPublicCallerArrivesThroughTheEdge_ChargesTheIpBucketWithoutACookie() throws Exception {
        MockHttpServletResponse response = readDetail(PUBLIC_CLIENT_IP);

        assertThat(deviceIdCookies(response)).isEmpty();
        verify(rateLimitService).check(RateLimitPolicy.PUBLIC_READ, "ip:" + PUBLIC_CLIENT_IP);
        verify(queryService).getPublishedPlanner(eq(PLANNER_ID), any(), eq("ip:" + PUBLIC_CLIENT_IP), any());
    }

    @Test
    void publishedDetail_WhenLoopbackPeerHasNoCookie_ChargesAndViewsUnderTheMintedDevice() throws Exception {
        MockHttpServletResponse response = readDetail(null);

        List<String> cookies = deviceIdCookies(response);
        assertThat(cookies).hasSize(1);
        String minted = cookies.get(0).substring((CookieConstants.DEVICE_ID + "=").length()).split(";", 2)[0];
        String identity = "device:" + UUID.fromString(minted);
        verify(rateLimitService).check(RateLimitPolicy.PUBLIC_READ, identity);
        verify(queryService).getPublishedPlanner(eq(PLANNER_ID), any(), eq(identity), any());
    }

    @Test
    void publishedDetail_WhenTheSamePublicIpReadsTwice_ChargesOneIpBucket() throws Exception {
        readDetail(PUBLIC_CLIENT_IP);
        readDetail(PUBLIC_CLIENT_IP);

        verify(rateLimitService, times(2)).check(RateLimitPolicy.PUBLIC_READ, "ip:" + PUBLIC_CLIENT_IP);
        verifyNoMoreInteractions(rateLimitService);
    }
}
