package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service for planner-specific sync events.
 *
 * <p>Routes planner update events through the central SseService
 * for settings-aware delivery to connected devices.</p>
 */
@Service
@Slf4j
public class PlannerSyncEventService {

    private final SseService sseService;

    public PlannerSyncEventService(SseService sseService) {
        this.sseService = sseService;
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
        Map<String, String> data = Map.of(
                "plannerId", plannerId.toString(),
                "type", eventType
        );

        sseService.sendToUser(userId, excludeDeviceId, "sync:planner", data);
        log.debug("Sent planner-update event: user={}, planner={}, type={}", userId, plannerId, eventType);
    }

    /**
     * Get the count of active connections for a user.
     *
     * @param userId the user ID
     * @return the number of active SSE connections
     */
    public int getActiveConnectionCount(Long userId) {
        return sseService.getActiveConnectionCount(userId);
    }
}
