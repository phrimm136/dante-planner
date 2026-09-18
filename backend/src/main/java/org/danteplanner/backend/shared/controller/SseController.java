package org.danteplanner.backend.shared.controller;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final SseService sseService;

    @RateLimited(RateLimitPolicy.SSE)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId) throws IOException {

        return sseService.subscribe(userId, deviceId);
    }
}
