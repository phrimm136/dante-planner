package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class PlannerValidationException extends RuntimeException {

    private final String errorCode;

    public PlannerValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
