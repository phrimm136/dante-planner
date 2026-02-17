package org.danteplanner.backend.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PlannerValidationException extends RuntimeException {

    private final String errorCode;

    @Setter
    private String failedContent;

    public PlannerValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
