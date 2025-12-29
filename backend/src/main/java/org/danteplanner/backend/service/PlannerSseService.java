package org.danteplanner.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing Server-Sent Events (SSE) connections for real-time
 * planner notifications across multiple devices.
 *
 * <p>Each user can have multiple connected devices, and this service manages
 * emitters per user-device combination. When a planner is modified, all connected
 * devices for that user (except the originating device) are notified.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerSseService {

    private static final long SSE_TIMEOUT_MS = 3600_000L; // 1 hour
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L; // 10 seconds per spec

    private final ObjectMapper objectMapper;

    // Map userId -> List of EmitterEntry (device + emitter pairs)
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<EmitterEntry>> emitters = new ConcurrentHashMap<>();

    /**
     * Internal record to associate a device ID with its SSE emitter.
     */
    private record EmitterEntry(UUID deviceId, SseEmitter emitter) {}

    /**
     * Subscribe a device to receive SSE events for a user.
     *
     * @param userId   the user ID
     * @param deviceId the device identifier (UUID)
     * @return the SSE emitter for the connection
     */
    public SseEmitter subscribe(Long userId, UUID deviceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Add to map
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(new EmitterEntry(deviceId, emitter));

        // Setup cleanup callbacks
        emitter.onCompletion(() -> removeEmitter(userId, deviceId));
        emitter.onTimeout(() -> removeEmitter(userId, deviceId));
        emitter.onError(e -> removeEmitter(userId, deviceId));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException e) {
            log.warn("Failed to send connected event to user {} device {}", userId, deviceId);
        }

        log.info("SSE subscribed: user={}, device={}", userId, deviceId);
        return emitter;
    }

    /**
     * Notify all connected devices of a user about a planner update,
     * except the device that originated the change.
     *
     * @param userId          the user ID
     * @param excludeDeviceId the device ID to exclude from notification (can be null)
     * @param plannerId       the ID of the affected planner
     * @param eventType       the type of event (created, updated, deleted)
     */
    public void notifyPlannerUpdate(Long userId, UUID excludeDeviceId, UUID plannerId, String eventType) {
        var userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        String data;
        try {
            data = objectMapper.writeValueAsString(
                    Map.of("plannerId", plannerId.toString(), "type", eventType)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize planner update event", e);
            return;
        }

        for (EmitterEntry entry : userEmitters) {
            // Skip the originating device if specified
            if (excludeDeviceId != null && entry.deviceId().equals(excludeDeviceId)) {
                continue;
            }

            try {
                entry.emitter().send(SseEmitter.event().name("planner-update").data(data));
                log.debug("Sent planner-update to user {} device {}: {}", userId, entry.deviceId(), eventType);
            } catch (IOException e) {
                log.warn("Failed to send to user {} device {}, removing emitter", userId, entry.deviceId());
                removeEmitter(userId, entry.deviceId());
            }
        }
    }

    /**
     * Send heartbeat comments to all connected emitters to keep connections alive.
     * This method is scheduled to run at a fixed rate.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeats() {
        emitters.forEach((userId, entries) -> {
            for (EmitterEntry entry : entries) {
                try {
                    entry.emitter().send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for user {} device {}, removing emitter", userId, entry.deviceId());
                    removeEmitter(userId, entry.deviceId());
                }
            }
        });
    }

    /**
     * Remove an emitter for a specific user and device.
     *
     * @param userId   the user ID
     * @param deviceId the device ID (UUID)
     */
    private void removeEmitter(Long userId, UUID deviceId) {
        var entries = emitters.get(userId);
        if (entries != null) {
            entries.removeIf(e -> e.deviceId().equals(deviceId));
            if (entries.isEmpty()) {
                emitters.remove(userId);
            }
        }
        log.debug("SSE unsubscribed: user={}, device={}", userId, deviceId);
    }

    /**
     * Get the count of active connections for a user (for monitoring/debugging).
     *
     * @param userId the user ID
     * @return the number of active SSE connections
     */
    public int getActiveConnectionCount(Long userId) {
        var entries = emitters.get(userId);
        return entries != null ? entries.size() : 0;
    }
}
