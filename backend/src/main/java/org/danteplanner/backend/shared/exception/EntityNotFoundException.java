package org.danteplanner.backend.shared.exception;

/**
 * Base "entity absent by id" error, mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 *
 * <p>Lives in {@code shared} so the read seam and the exception handler can express "not found"
 * without depending on any feature package. Feature-specific not-found exceptions extend this to
 * carry their own typed id (e.g. planner's {@code PlannerNotFoundException} adds {@code plannerId}).
 * The handler keys the 404 on this base, so every subclass is covered by polymorphism while a bare
 * instance — thrown where the concrete type is not known — still maps cleanly.</p>
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found with id: " + id);
    }
}
