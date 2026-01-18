package org.danteplanner.backend.exception;

import io.sentry.Sentry;
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

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFoundException ex) {
        log.warn("Comment not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("COMMENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CommentForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleCommentForbidden(CommentForbiddenException ex) {
        log.warn("Comment access forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("COMMENT_FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ErrorResponse> handleTokenRevoked(TokenRevokedException ex) {
        Sentry.captureException(ex);
        log.warn("Token revoked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("TOKEN_REVOKED", ex.getMessage()));
    }

    @ExceptionHandler(AccountDeletedException.class)
    public ResponseEntity<ErrorResponse> handleAccountDeleted(AccountDeletedException ex) {
        Sentry.captureException(ex);
        log.warn("Account deleted: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("ACCOUNT_DELETED", ex.getMessage()));
    }

    @ExceptionHandler(UserTimedOutException.class)
    public ResponseEntity<ErrorResponse> handleUserTimedOut(UserTimedOutException ex) {
        log.warn("User timed out: user {} until {}", ex.getUserId(), ex.getTimeoutUntil());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("USER_TIMED_OUT", "Your account is temporarily restricted until " + ex.getTimeoutUntil()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        Sentry.captureException(ex);
        log.warn("Invalid token [{}]: {}", ex.getReason(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<ErrorResponse> handleOAuthException(OAuthException ex) {
        Sentry.captureException(ex);
        log.error("OAuth error for provider {} during {}: {}",
            ex.getProvider(), ex.getOperation(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("OAUTH_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(UsernameGenerationException.class)
    public ResponseEntity<ErrorResponse> handleUsernameGeneration(UsernameGenerationException ex) {
        Sentry.captureException(ex);
        log.error("Username generation failed after {} attempts", ex.getAttemptsMade());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("USERNAME_GENERATION_FAILED", "Unable to create account. Please try again."));
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

    @ExceptionHandler(VoteAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleVoteAlreadyExists(VoteAlreadyExistsException ex) {
        log.warn("Duplicate vote attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("VOTE_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(ReportAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleReportAlreadyExists(ReportAlreadyExistsException ex) {
        log.warn("Duplicate report attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("REPORT_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(CommentReportAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCommentReportAlreadyExists(CommentReportAlreadyExistsException ex) {
        log.warn("Duplicate comment report attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("COMMENT_REPORT_ALREADY_EXISTS", ex.getMessage()));
    }

    /**
     * User-fixable error codes that are safe to expose to clients.
     * These help users understand how to fix their content.
     *
     * <p>All other error codes (INVALID_JSON, MISSING_REQUIRED_FIELD, UNKNOWN_FIELD,
     * INVALID_CATEGORY, INVALID_FIELD_TYPE, INVALID_ID_REFERENCE) are structural
     * validation errors that reveal API schema details and are mapped to generic
     * VALIDATION_ERROR to prevent information disclosure and schema probing attacks.</p>
     */
    private static final java.util.Set<String> USER_FACING_ERROR_CODES = java.util.Set.of(
            "EMPTY_CONTENT",      // User can provide content
            "SIZE_EXCEEDED",      // User can reduce content size
            "MALFORMED_JSON"      // User can fix JSON syntax
    );

    @ExceptionHandler(PlannerValidationException.class)
    public ResponseEntity<ErrorResponse> handlePlannerValidation(PlannerValidationException ex) {
        log.warn("Planner validation error [{}]: {}", ex.getErrorCode(), ex.getMessage());

        // Return granular error codes for user-fixable issues
        // Return generic code for structural issues to prevent information disclosure
        if (USER_FACING_ERROR_CODES.contains(ex.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
        }

        // Generic error for structural validation to prevent probing
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", "Invalid planner content structure"));
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
        Sentry.captureException(ex);
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
