package org.danteplanner.backend.shared.exception;

public class EntityNotFoundException extends DomainException {

    private static final String DEFAULT_ERROR_CODE = "NOT_FOUND";

    public EntityNotFoundException(String message) {
        super(ErrorKind.NOT_FOUND, DEFAULT_ERROR_CODE, message);
    }

    public EntityNotFoundException(String entityType, Object id) {
        super(ErrorKind.NOT_FOUND, DEFAULT_ERROR_CODE, entityType + " not found with id: " + id);
    }

    protected EntityNotFoundException(String errorCode, String message) {
        super(ErrorKind.NOT_FOUND, errorCode, message);
    }
}
