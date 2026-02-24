package org.danteplanner.backend.exception;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.UUID;
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
            .body(new ErrorResponse("UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(AccountDeletedException.class)
    public ResponseEntity<ErrorResponse> handleAccountDeleted(AccountDeletedException ex) {
        Sentry.captureException(ex);
        log.warn("Account deleted: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(UserTimedOutException.class)
    public ResponseEntity<ErrorResponse> handleUserTimedOut(UserTimedOutException ex) {
        log.warn("User timed out: user {} until {}", ex.getUserId(), ex.getTimeoutUntil());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("USER_TIMED_OUT", "Your account is temporarily restricted until " + ex.getTimeoutUntil()));
    }

    @ExceptionHandler(UserBannedException.class)
    public ResponseEntity<ErrorResponse> handleUserBanned(UserBannedException ex) {
        log.warn("User banned: user {} since {}", ex.getUserId(), ex.getBannedAt());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("USER_BANNED", "Your account has been suspended"));
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
            .body(new ErrorResponse("UNAUTHORIZED", "Authentication required"));
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
        Sentry.captureException(ex);
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
     * <p>All other error codes (MISSING_REQUIRED_FIELD, UNKNOWN_FIELD,
     * INVALID_CATEGORY, INVALID_FIELD_TYPE, INVALID_ID_REFERENCE,
     * VALUE_OUT_OF_RANGE, DUPLICATE_VALUE, INVALID_SEQUENCE, GIFT_NOT_AFFORDABLE)
     * are structural validation errors that reveal API schema details and are mapped to generic
     * VALIDATION_ERROR to prevent information disclosure and schema probing attacks.</p>
     */
    private static final java.util.Set<String> USER_FACING_ERROR_CODES = java.util.Set.of(
            "EMPTY_CONTENT",      // User can provide content
            "SIZE_EXCEEDED",      // User can reduce content size
            "MALFORMED_JSON"      // User can fix JSON syntax
    );

    @ExceptionHandler(PlannerValidationException.class)
    public ResponseEntity<ErrorResponse> handlePlannerValidation(PlannerValidationException ex) {
        if (USER_FACING_ERROR_CODES.contains(ex.getErrorCode())) {
            log.warn("Planner validation error [{}]: {}", ex.getErrorCode(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
        }

        // Multi-error: log each sub-error individually for CloudWatch searchability
        if (!ex.getSubErrors().isEmpty()) {
            ex.getSubErrors().forEach(e ->
                log.warn("Planner validation error [{}]: {}", e.code(), e.message()));
        } else {
            log.warn("Planner validation error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }

        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", "Invalid planner content structure"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Validation error: {} | body: {}", message, ex.getBindingResult().getTarget());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.equals(UUID.class)) {
            log.warn("Invalid UUID format for parameter '{}': {}", ex.getName(), ex.getValue());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Resource not found"));
        }
        log.warn("Type mismatch for parameter '{}': expected {}, got {}",
            ex.getName(), requiredType != null ? requiredType.getSimpleName() : "unknown", ex.getValue());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", "Invalid parameter format"));
    }

    /**
     * Handle database constraint violations (PRIMARY KEY, UNIQUE, FOREIGN KEY, NOT NULL).
     *
     * <p>UUID collisions from race conditions are expected and return 409 Conflict.
     * Other constraint violations indicate bugs and are sent to Sentry.</p>
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {

        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        // Detect PRIMARY KEY or UNIQUE constraint violations
        if (message.contains("duplicate") ||
            message.contains("unique") ||
            message.contains("primary key")) {

            // UUID collision on planners table (expected race condition)
            if (message.contains("planners") && (message.contains("primary") || message.contains("id"))) {
                log.warn("UUID collision detected (race condition): {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                        "UUID_COLLISION",
                        "Plan ID already exists. Please retry with a new ID."
                    ));
            }

            // Duplicate vote/bookmark/report (expected user behavior, but should be caught earlier)
            if (message.contains("planner_votes") ||
                message.contains("planner_bookmarks") ||
                message.contains("planner_reports") ||
                message.contains("comment_reports")) {
                log.warn("Duplicate action bypassed application check: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("DUPLICATE_ACTION", "Action already performed"));
            }

            // Other unique constraint violations (unexpected)
            log.warn("Unexpected unique constraint violation: {}", ex.getMessage());
            Sentry.captureException(ex);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", "Resource conflict"));
        }

        // Foreign key or NOT NULL violations (indicate bugs)
        Sentry.captureException(ex);
        log.error("Database constraint violation", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST", "Invalid data"));
    }

    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    public ResponseEntity<ErrorResponse> handleCannotAcquireLock(org.springframework.dao.CannotAcquireLockException ex) {
        log.warn("Database deadlock detected: {}", ex.getMessage());
        // Return 503 Service Unavailable with retry-after hint
        // Client should retry the request (view recording is idempotent)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("DEADLOCK", "Database temporarily busy, please retry"));
    }

    /**
     * Handle SSE client disconnections (broken pipe, connection reset).
     *
     * <p>When clients disconnect from SSE endpoints (browser close, network interruption),
     * Spring may throw IOException when attempting to write to the closed socket.
     * This is expected behavior and should be logged at DEBUG level, not ERROR.</p>
     *
     * <p>Common scenarios:
     * <ul>
     *   <li>User closes browser tab</li>
     *   <li>Network interruption</li>
     *   <li>Client timeout</li>
     *   <li>Explicit connection close from client</li>
     * </ul>
     * </p>
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String className = ex.getClass().getName();

        // SSE client disconnection (broken pipe, connection reset)
        // Also catches Tomcat's ClientAbortException which extends IOException
        if (message.contains("broken pipe") ||
            message.contains("connection reset") ||
            message.contains("connection abort") ||
            message.contains("stream closed") ||
            className.contains("ClientAbortException")) {
            log.debug("SSE client disconnected ({}): {}", className, ex.getMessage());
            return;
        }

        // Other IOExceptions are unexpected and should be sent to Sentry
        Sentry.captureException(ex);
        log.error("Unexpected IOException", ex);
    }

    /**
     * Handle async request timeouts (SSE connection timeouts).
     *
     * <p>When SSE connections reach their configured timeout (default 1 hour),
     * Spring throws AsyncRequestTimeoutException. This is expected behavior
     * as clients should reconnect periodically.</p>
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        log.debug("SSE connection timeout: {}", ex.getMessage());
    }

    /**
     * Handle async request not usable exceptions (SSE connection already completed/closed).
     *
     * <p>When Spring tries to write to an SSE connection that's already completed,
     * timed out, or in an invalid state, it throws AsyncRequestNotUsableException.
     * This commonly occurs during rapid disconnect/reconnect scenarios or when
     * cleanup logic runs on already-closed connections.</p>
     *
     * <p>Common scenarios:
     * <ul>
     *   <li>Client disconnects while server is writing</li>
     *   <li>Heartbeat/cleanup tries to write to completed connection</li>
     *   <li>Race condition between timeout and send</li>
     * </ul>
     * </p>
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        log.debug("SSE connection not usable (already completed/closed): {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        Sentry.captureException(ex);
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private static final int MAX_LOG_CONTENT_LENGTH = 1000;

    private static String truncate(String content) {
        if (content == null) {
            return "<null>";
        }
        if (content.length() <= MAX_LOG_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_LOG_CONTENT_LENGTH) + "...(truncated)";
    }
}
