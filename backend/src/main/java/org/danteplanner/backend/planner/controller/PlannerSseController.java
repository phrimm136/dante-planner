package org.danteplanner.backend.planner.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller for planner Server-Sent Events subscriptions.
 *
 * <p>Returns an SSE stream that emits events when planners are created,
 * updated, or deleted on other devices, enabling cross-device sync.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerSseController {

    private final SseService sseService;
    private final RateLimitService rateLimitService;

    /**
     * Subscribe to Server-Sent Events for planner updates.
     *
     * <p>Returns an SSE stream that sends events when planners are
     * created, updated, or deleted on other devices.</p>
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @return the SSE emitter
     */
    @RateLimited(RateLimitPolicy.SSE)
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId) {

        rateLimitService.check(RateLimitPolicy.SSE, userId);
        log.info("SSE subscription for user {} device {}", userId, deviceId);
        return sseService.subscribe(userId, deviceId);
    }
}
