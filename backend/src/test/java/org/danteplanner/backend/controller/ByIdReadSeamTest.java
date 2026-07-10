package org.danteplanner.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.danteplanner.backend.planner.controller.PlannerQueryController;
import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.RateLimitConfig;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Proves both byId GET endpoints route their service dereference through
 * {@link ByIdReadGuard}. The guard is a real pass-through wrapped in a spy, so the
 * supplier still executes (service value flows through) while invocation is verifiable.
 */
@ExtendWith(MockitoExtension.class)
class ByIdReadSeamTest {

    @Spy
    ByIdReadGuard byIdReadGuard = new ByIdReadGuard();

    @Mock
    PlannerQueryService plannerQueryService;

    @Mock
    RateLimitConfig rateLimitConfig;

    @Mock
    PublishedPlannerQueryService publishedPlannerQueryService;

    @Mock
    SecurityProperties securityProperties;

    @Mock
    HttpServletRequest request;

    @InjectMocks
    PlannerQueryController plannerQueryController;

    @InjectMocks
    PublishedPlannerController publishedPlannerController;

    @Test
    void getPlanner_WhenInvoked_RoutesThroughByIdReadGuard() {
        Long userId = 42L;
        UUID id = UUID.randomUUID();
        PlannerResponse expected = mock(PlannerResponse.class);
        when(plannerQueryService.getPlanner(userId, id)).thenReturn(expected);

        ResponseEntity<PlannerResponse> result = plannerQueryController.getPlanner(userId, id);

        assertThat(result.getBody()).isSameAs(expected);
        verify(byIdReadGuard).read(eq("planner"), eq(id), any());
    }

    @Test
    void getPublishedPlanner_WhenInvoked_RoutesThroughByIdReadGuard() {
        Long userId = 42L;
        UUID id = UUID.randomUUID();
        PublishedPlannerDetailResponse expected = mock(PublishedPlannerDetailResponse.class);
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(securityProperties.isTrustedProxy("1.2.3.4")).thenReturn(false);
        when(request.getHeader("User-Agent")).thenReturn("agent");
        when(publishedPlannerQueryService.getPublishedPlanner(eq(id), eq(userId), anyString(), any()))
                .thenReturn(expected);

        ResponseEntity<PublishedPlannerDetailResponse> result =
                publishedPlannerController.getPublishedPlanner(request, id, userId);

        assertThat(result.getBody()).isSameAs(expected);
        verify(byIdReadGuard).read(eq("planner"), eq(id), any());
    }
}
