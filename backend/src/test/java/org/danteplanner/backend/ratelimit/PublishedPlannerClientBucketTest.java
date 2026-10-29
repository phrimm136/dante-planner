package org.danteplanner.backend.ratelimit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.support.TestJsonMappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ProblemWriter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.support.InMemoryProxyManager;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins which bucket the anonymous detail read charges and which identity its view carries, for a
 * caller keyed by address and for one keyed by device.
 */
@ExtendWith(MockitoExtension.class)
class PublishedPlannerClientBucketTest {

    private static final String TRUSTED_PROXY_IP = "127.0.0.1";
    private static final String PUBLIC_CLIENT_IP = "203.0.113.7";
    private static final UUID PLANNER_ID = UUID.fromString("0b7e8f3a-1c2d-4e5f-8a9b-0c1d2e3f4a5b");

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private PlannerViewRepository plannerViewRepository;

    private final InMemoryProxyManager rateLimitStore = new InMemoryProxyManager();
    private PlannerViewRecorder viewRecorder;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.unsavedUser(1L);
        Planner planner = TestDataFactory.planner(owner).id(PLANNER_ID).published(true).build();
        when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));
        viewRecorder = new PlannerViewRecorder(plannerViewRepository, plannerStatsRepository);
        PublishedPlannerQueryService queryService = new PublishedPlannerQueryService(null, null, null, null, null,
                viewRecorder, null, plannerStatsRepository, new PlannerAccessGuard(null, plannerRepository), null);
        ObjectMapper objectMapper = TestJsonMappers.springDefaults();

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setTrustedProxyIps(TRUSTED_PROXY_IP);
        securityProperties.parseTrustedProxyIps();
        DeviceIdResolver deviceIdResolver = new DeviceIdResolver(new CookieUtils(false, "", "Lax"));

        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitService(rateLimitStore, publicReadBucket()),
                securityProperties,
                deviceIdResolver,
                new FrontendProperties("https://planner.example"),
                new ProblemWriter(objectMapper),
                new SimpleMeterRegistry());
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

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
        assertThat(chargedKeys()).containsExactly("ip:" + PUBLIC_CLIENT_IP + ":public-read");
        assertThat(viewedHashes()).containsExactly(
                ViewerHashUtil.hashForAnonymousUser("ip:" + PUBLIC_CLIENT_IP, null, PLANNER_ID));
    }

    @Test
    void publishedDetail_WhenLoopbackPeerHasNoCookie_ChargesAndViewsUnderTheMintedDevice() throws Exception {
        MockHttpServletResponse response = readDetail(null);

        List<String> cookies = deviceIdCookies(response);
        assertThat(cookies).hasSize(1);
        String minted = cookies.get(0).substring((CookieConstants.DEVICE_ID + "=").length()).split(";", 2)[0];
        String identity = "device:" + UUID.fromString(minted);
        assertThat(chargedKeys()).containsExactly(identity + ":public-read");
        assertThat(viewedHashes()).containsExactly(ViewerHashUtil.hashForAnonymousUser(identity, null, PLANNER_ID));
    }

    @Test
    void publishedDetail_WhenTheSamePublicIpReadsTwice_ChargesOneIpBucket() throws Exception {
        readDetail(PUBLIC_CLIENT_IP);
        readDetail(PUBLIC_CLIENT_IP);

        assertThat(chargedKeys()).containsExactly(
                "ip:" + PUBLIC_CLIENT_IP + ":public-read", "ip:" + PUBLIC_CLIENT_IP + ":public-read");
    }

    private List<String> chargedKeys() {
        return rateLimitStore.charges().stream().map(InMemoryProxyManager.Charge::key).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> viewedHashes() {
        TransactionSynchronizationManager.initSynchronization();
        viewRecorder.flush();
        ArgumentCaptor<Collection<PlannerViewId>> drained = ArgumentCaptor.forClass(Collection.class);
        verify(plannerViewRepository).insertIgnoreReturningNewCounts(drained.capture(), any(Instant.class));
        return drained.getValue().stream().map(PlannerViewId::getViewerHash).toList();
    }

    private static RateLimitProperties publicReadBucket() {
        RateLimitProperties.BucketConfig bucket = new RateLimitProperties.BucketConfig();
        bucket.setCapacity(10);
        bucket.setRefillTokens(10);
        bucket.setRefillDurationSeconds(60);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPublicRead(bucket);
        return properties;
    }
}
