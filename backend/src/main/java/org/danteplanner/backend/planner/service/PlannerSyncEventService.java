package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.event.PlannerSyncEvent;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Service for planner-specific sync events.
 *
 * <p>Routes planner update events through the central SseService
 * for settings-aware delivery to connected devices.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerSyncEventService {

    private final SseService sseService;
    private final SsePublisher ssePublisher;

    /**
     * Announce a committed planner write to the owner's other devices.
     *
     * @param event the committed write
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlannerSynced(PlannerSyncEvent event) {
        notifyPlannerUpdate(event.userId(), event.excludeDeviceId(), event.plannerId(),
                event.eventType(), event.payload());
    }

    /**
     * Notify all connected devices of a user about a planner update,
     * except the device that originated the change.
     *
     * @param userId          the user ID
     * @param excludeDeviceId the device ID to exclude from notification (can be null)
     * @param plannerId       the ID of the affected planner
     * @param eventType       the type of event
     * @param payload         the changed planner row recipients patch into their caches;
     *                        null when the event carries no row (deleted)
     */
    public void notifyPlannerUpdate(Long userId, UUID excludeDeviceId, UUID plannerId, SseEventType eventType,
            Object payload) {
        ssePublisher.publishUserEvent(userId, excludeDeviceId, eventType,
                plannerId.toString(), payload);
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
