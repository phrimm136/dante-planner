package org.danteplanner.backend.shared.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.config.RateLimitConfig;
import org.danteplanner.backend.shared.sse.SseService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller for Server-Sent Events subscriptions.
 *
 * <p>Provides a unified SSE endpoint for all real-time notifications
 * including planner sync events and user notifications.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
@Slf4j
public class SseController {

    private final SseService sseService;
    private final RateLimitConfig rateLimitConfig;

    /**
     * Subscribe to Server-Sent Events for all user notifications.
     *
     * <p>Returns an SSE stream that sends events based on user settings:
     * sync:planner, notify:comment, notify:recommended, notify:published.</p>
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @return the SSE emitter
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId) {

        rateLimitConfig.checkSseLimit(userId);
        log.info("SSE subscription for user {} device {}", userId, deviceId);
        return sseService.subscribe(userId, deviceId);
    }
}
