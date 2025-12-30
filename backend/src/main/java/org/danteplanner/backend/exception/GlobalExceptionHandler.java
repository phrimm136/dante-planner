package org.danteplanner.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public record ErrorResponse(String code, String message) {}

    public record ConflictErrorResponse(String code, String message, Long serverVersion) {}

    @ExceptionHandler(PlannerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlannerNotFound(PlannerNotFoundException ex) {
        log.warn("Planner not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PLANNER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(PlannerForbiddenException.class)
    public ResponseEntity<ErrorResponse> handlePlannerForbidden(PlannerForbiddenException ex) {
        log.warn("Planner access forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("PLANNER_FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(PlannerConflictException.class)
    public ResponseEntity<ConflictErrorResponse> handlePlannerConflict(PlannerConflictException ex) {
        log.warn("Planner sync conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ConflictErrorResponse("SYNC_CONFLICT", ex.getMessage(), ex.getActualVersion()));
    }

    @ExceptionHandler(PlannerLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePlannerLimitExceeded(PlannerLimitExceededException ex) {
        log.warn("Planner limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("PLANNER_LIMIT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(PlannerValidationException.class)
    public ResponseEntity<ErrorResponse> handlePlannerValidation(PlannerValidationException ex) {
        log.warn("Planner validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
