package org.danteplanner.backend.planner.event;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.shared.entity.SseEventType;

import java.util.UUID;

/**
 * A planner write that the owner's other devices still have to hear about.
 *
 * <p>Carries the finished response rather than the entity: the listener runs after commit with no
 * live transaction and no session to load a lazy graph from.</p>
 *
 * @param userId          the owner whose devices receive the event
 * @param excludeDeviceId the device that originated the change, excluded from delivery; null when
 *                        the change came from no device
 * @param plannerId       the planner the event is about
 * @param eventType       the kind of write that happened
 * @param payload         the changed planner row recipients patch into their caches; null when the
 *                        event carries no row (deleted)
 */
public record PlannerSyncEvent(
        Long userId,
        UUID excludeDeviceId,
        UUID plannerId,
        SseEventType eventType,
        PlannerResponse payload) {

    /**
     * Announces a deleted planner, which carries no row to patch into a cache.
     *
     * @param userId          the owner whose devices receive the event
     * @param excludeDeviceId the device that originated the deletion, or null when the change came
     *                        from no device
     * @param plannerId       the planner that was deleted
     * @return the event
     */
    public static PlannerSyncEvent deleted(Long userId, UUID excludeDeviceId, UUID plannerId) {
        return new PlannerSyncEvent(userId, excludeDeviceId, plannerId, SseEventType.DELETED, null);
    }
}
