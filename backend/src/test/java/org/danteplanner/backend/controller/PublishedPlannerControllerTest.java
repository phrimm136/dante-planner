package org.danteplanner.backend.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.planner.service.RedisViewRecorder;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.util.CookieUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishedPlannerControllerTest {

    private static final UUID PLANNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String CLIENT_IP = "203.0.113.7";
    private static final String HASH_WITHOUT_USER_AGENT =
            "2d7e35092bd0dfaf2dbb314e8edb611440cd7d809e581b56d4844b9e7625e0f6";

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Captor
    private ArgumentCaptor<List<String>> keys;

    @Test
    void recordView_WhenNoUserAgentIsSent_RecordsTheHashOfAnEmptyAgent() {
        when(plannerRepository.findThread(PLANNER_ID))
                .thenReturn(Optional.of(new PlannerThread(PLANNER_ID, 1L, true, false)));
        PublishedPlannerQueryService queryService = new PublishedPlannerQueryService(null, null, null, null, null,
                new RedisViewRecorder(redisTemplate, new SimpleMeterRegistry()), null,
                new PlannerAccessGuard(null, plannerRepository), null);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setTrustedProxyIps("127.0.0.1");
        securityProperties.parseTrustedProxyIps();
        PublishedPlannerController controller = new PublishedPlannerController(queryService,
                securityProperties,new DeviceIdResolver(new CookieUtils(false, "", "Lax")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);

        controller.recordView(request, new MockHttpServletResponse(), PLANNER_ID, null);

        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(), any());
        assertThat(keys.getValue().getFirst()).endsWith(":" + PLANNER_ID + ":" + HASH_WITHOUT_USER_AGENT);
    }
}
