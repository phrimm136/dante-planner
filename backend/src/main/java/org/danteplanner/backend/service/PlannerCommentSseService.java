package org.danteplanner.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE service for planner comment notifications.
 *
 * <p>Unlike {@link SseService} which is user-centric, this service is planner-centric.
 * Any device (authenticated or guest) can subscribe to a planner's comment feed
 * and receive notifications when new comments are posted.</p>
 *
 * <p>Key differences from SseService:
 * <ul>
 *   <li>Keyed by plannerId instead of userId</li>
 *   <li>No authentication required (guests can subscribe)</li>
 *   <li>No settings filtering (all subscribers receive all events)</li>
 *   <li>Author's device is excluded when broadcasting</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class PlannerCommentSseService {

    private static final long SSE_TIMEOUT_MS = 3600_000L; // 1 hour
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L; // 15 seconds
    private static final long CLEANUP_INTERVAL_MS = 60_000L; // 1 minute
    private static final int MAX_CONNECTIONS_PER_PLANNER = 500; // Prevent DoS

    private final ObjectMapper objectMapper;

    /**
     * Registry: plannerId → list of device connections
     */
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<EmitterEntry>> plannerEmitters = new ConcurrentHashMap<>();

    public PlannerCommentSseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private record EmitterEntry(UUID deviceId, SseEmitter emitter) {}

    /**
     * Subscribe a device to receive comment notifications for a planner.
     *
     * @param plannerId the planner ID to subscribe to
     * @param deviceId  the device identifier (from cookie)
     * @return the SSE emitter for the connection
     */
    public SseEmitter subscribe(UUID plannerId, UUID deviceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Remove existing emitter for same device (reconnection case)
        var subscribers = plannerEmitters.computeIfAbsent(plannerId, k -> new CopyOnWriteArrayList<>());
        subscribers.removeIf(e -> e.deviceId().equals(deviceId));

        // FIFO eviction if at max capacity (DoS prevention)
        while (subscribers.size() >= MAX_CONNECTIONS_PER_PLANNER && !subscribers.isEmpty()) {
            EmitterEntry oldest = subscribers.remove(0);
            try {
                oldest.emitter().complete();
            } catch (Exception e) {
                // Ignore completion errors
            }
            log.warn("Comment SSE: Evicted oldest connection for planner {} (max {} reached)",
                    plannerId, MAX_CONNECTIONS_PER_PLANNER);
        }

        subscribers.add(new EmitterEntry(deviceId, emitter));

        // Auto-cleanup on disconnect
        emitter.onCompletion(() -> removeConnection(plannerId, deviceId));
        emitter.onTimeout(() -> removeConnection(plannerId, deviceId));
        emitter.onError(e -> removeConnection(plannerId, deviceId));

        // Send initial connected event
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException e) {
            log.warn("Failed to send connected event for planner {} device {}", plannerId, deviceId);
        }

        log.debug("Comment SSE subscribed: planner={}, device={}", plannerId, deviceId);
        return emitter;
    }

    /**
     * Broadcast a comment:added event to all subscribers of a planner.
     * Excludes the author's device to prevent self-notification.
     *
     * @param plannerId       the planner ID
     * @param excludeDeviceId the device ID to exclude (author's device)
     */
    public void broadcastCommentAdded(UUID plannerId, UUID excludeDeviceId) {
        var subscribers = plannerEmitters.get(plannerId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for planner {} comment notification", plannerId);
            return;
        }

        Map<String, Object> payload = Map.of(
                "plannerId", plannerId.toString(),
                "timestamp", Instant.now().toString()
        );

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize comment:added event for planner {}", plannerId, e);
            return;
        }

        int sent = 0;
        for (EmitterEntry entry : subscribers) {
            // Skip the author's device
            if (excludeDeviceId != null && entry.deviceId().equals(excludeDeviceId)) {
                continue;
            }

            try {
                entry.emitter().send(SseEmitter.event().name("comment:added").data(jsonData));
                sent++;
            } catch (IOException e) {
                log.debug("Failed to send comment:added to planner {} device {}, removing", plannerId, entry.deviceId());
                removeConnection(plannerId, entry.deviceId());
            }
        }

        log.debug("Broadcast comment:added to {} subscribers for planner {}", sent, plannerId);
    }

    /**
     * Remove a connection for a specific planner and device.
     *
     * @param plannerId the planner ID
     * @param deviceId  the device ID
     */
    public void removeConnection(UUID plannerId, UUID deviceId) {
        var entries = plannerEmitters.get(plannerId);
        if (entries != null) {
            entries.removeIf(e -> e.deviceId().equals(deviceId));
            if (entries.isEmpty()) {
                plannerEmitters.remove(plannerId);
            }
        }
        log.debug("Comment SSE unsubscribed: planner={}, device={}", plannerId, deviceId);
    }

    /**
     * Get the count of active subscribers for a planner.
     *
     * @param plannerId the planner ID
     * @return the number of active SSE connections
     */
    public int getSubscriberCount(UUID plannerId) {
        var entries = plannerEmitters.get(plannerId);
        return entries != null ? entries.size() : 0;
    }

    /**
     * Get total connection count across all planners (for monitoring).
     *
     * @return total number of active connections
     */
    public int getTotalConnectionCount() {
        return plannerEmitters.values().stream()
                .mapToInt(CopyOnWriteArrayList::size)
                .sum();
    }

    /**
     * Send heartbeat to all connected emitters.
     * Uses different fixedRate to avoid collision with SseService heartbeats.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = 5000)
    public void sendHeartbeats() {
        plannerEmitters.forEach((plannerId, entries) -> {
            for (EmitterEntry entry : entries) {
                try {
                    entry.emitter().send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for planner {} device {}, removing", plannerId, entry.deviceId());
                    removeConnection(plannerId, entry.deviceId());
                }
            }
        });
    }

    /**
     * Cleanup zombie connections by probing all emitters.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS, initialDelay = 30000)
    public void cleanupZombieConnections() {
        int removed = 0;
        for (var plannerEntry : plannerEmitters.entrySet()) {
            UUID plannerId = plannerEntry.getKey();
            for (EmitterEntry entry : plannerEntry.getValue()) {
                try {
                    entry.emitter().send(SseEmitter.event().comment("probe"));
                } catch (IOException e) {
                    removeConnection(plannerId, entry.deviceId());
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.debug("Comment SSE cleanup removed {} zombie connections", removed);
        }
    }
}
