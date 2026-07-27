package org.danteplanner.backend.shared.exception;

/**
 * Base "entity absent by id" error, answered as a not-found outcome by {@link GlobalExceptionHandler}.
 *
 * <p>Lives in {@code shared} so the read seam and the exception handler can express "not found"
 * without depending on any feature package. Feature-specific not-found exceptions extend this to
 * carry their own typed id (e.g. planner's {@code PlannerNotFoundException} adds {@code plannerId})
 * and their own error code; a bare instance — thrown where the concrete type is not known — still
 * maps cleanly.</p>
 */
public class EntityNotFoundException extends DomainException {

    private static final String DEFAULT_ERROR_CODE = "NOT_FOUND";

    public EntityNotFoundException(String message) {
        super(ErrorKind.NOT_FOUND, DEFAULT_ERROR_CODE, message);
    }

    public EntityNotFoundException(String entityType, Object id) {
        super(ErrorKind.NOT_FOUND, DEFAULT_ERROR_CODE, entityType + " not found with id: " + id);
    }

    /**
     * For a subclass naming the absent thing precisely enough to carry its own client-facing code.
     *
     * @param errorCode the subclass's error code
     * @param message   the log-facing description
     */
    protected EntityNotFoundException(String errorCode, String message) {
        super(ErrorKind.NOT_FOUND, errorCode, message);
    }
}
