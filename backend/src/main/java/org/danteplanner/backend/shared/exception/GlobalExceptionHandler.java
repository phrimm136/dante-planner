package org.danteplanner.backend.shared.exception;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.exception.UserTimedOutException;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.auth.exception.TokenRevokedException;
import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final CookieUtils cookieUtils;

    public record ErrorResponse(String code, String message) {}

    public record ConflictErrorResponse(String code, String message, Long serverVersion) {}

    /**
     * Log a warning and build an error response for the simple warn -> status -> body handler families.
     *
     * @param status    the HTTP status to return
     * @param logFormat the SLF4J warn format (single {} placeholder for the message)
     * @param code      the client-facing error code
     * @param message   the error message, used for both the log and the response body
     * @return the error response entity
     */
    private ResponseEntity<ErrorResponse> warnAndRespond(HttpStatus status, String logFormat, String code, String message) {
        log.warn(logFormat, message);
        return ResponseEntity.status(status)
            .body(new ErrorResponse(code, message));
    }

    @ExceptionHandler(PlannerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlannerNotFound(PlannerNotFoundException ex) {
        return warnAndRespond(HttpStatus.NOT_FOUND, "Planner not found: {}", "PLANNER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PlannerForbiddenException.class)
    public ResponseEntity<ErrorResponse> handlePlannerForbidden(PlannerForbiddenException ex) {
        return warnAndRespond(HttpStatus.FORBIDDEN, "Planner access forbidden: {}", "PLANNER_FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return warnAndRespond(HttpStatus.NOT_FOUND, "User not found: {}", "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFoundException ex) {
        return warnAndRespond(HttpStatus.NOT_FOUND, "Comment not found: {}", "COMMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CommentForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleCommentForbidden(CommentForbiddenException ex) {
        return warnAndRespond(HttpStatus.FORBIDDEN, "Comment access forbidden: {}", "COMMENT_FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ErrorResponse> handleTokenRevoked(TokenRevokedException ex) {
        Sentry.captureException(ex);
        log.warn("Token revoked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<ErrorResponse> handleSessionRevoked(SessionRevokedException ex, HttpServletResponse response) {
        Sentry.captureException(ex);
        log.warn("Session revoked: {}", ex.getMessage());
        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);
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
        return warnAndRespond(HttpStatus.CONFLICT, "Duplicate vote attempt: {}", "VOTE_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(ReportAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleReportAlreadyExists(ReportAlreadyExistsException ex) {
        return warnAndRespond(HttpStatus.CONFLICT, "Duplicate report attempt: {}", "REPORT_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(CommentReportAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCommentReportAlreadyExists(CommentReportAlreadyExistsException ex) {
        return warnAndRespond(HttpStatus.CONFLICT, "Duplicate comment report attempt: {}", "COMMENT_REPORT_ALREADY_EXISTS", ex.getMessage());
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", "Invalid request body"));
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
     * Handle the database being briefly unreachable (RDS maintenance reboot, failover, network blip).
     *
     * <p>When the DB is down, HikariCP cannot hand out a connection. Spring surfaces this as one of
     * two unrelated hierarchies depending on WHERE the connection was needed: a query that runs
     * outside a transaction yields DataAccessResourceFailureException (CannotGetJdbcConnectionException
     * is a subclass); a {@code @Transactional} method fails at transaction-begin and yields
     * CannotCreateTransactionException (a TransactionException, NOT a DataAccessException). Both mean
     * the same thing — the DB is unreachable — so both map to 503 here. This is transient and
     * self-healing — the pool reconnects when the DB returns. Deliberately NOT sent to Sentry: it is
     * expected during the weekly single-AZ
     * maintenance window and would otherwise alert-storm. Scoped to the resource-failure branch
     * only, so query/constraint bugs keep their own handlers and are never masked as 503.</p>
     *
     * <p>The point of returning 503 (not letting it fall to the catch-all 500) is the edge contract:
     * nginx has {@code proxy_intercept_errors on; error_page 502 503 504 = @backend_error}, so it
     * rewrites any backend 5xx body to {@code BACKEND_UNAVAILABLE} (or {@code SERVICE_UPDATING}).
     * A 500 would NOT be intercepted and would leak through as a raw INTERNAL_ERROR. So this handler
     * exists to (a) emit 503 so nginx maps it cleanly to BACKEND_UNAVAILABLE for the client, and
     * (b) keep it out of Sentry. The {@code WRITE_TEMPORARILY_UNAVAILABLE} code below is internal-only (logs /
     * direct backend access); external clients always see BACKEND_UNAVAILABLE.</p>
     */
    @ExceptionHandler({
            org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.transaction.CannotCreateTransactionException.class
    })
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(
            org.springframework.core.NestedRuntimeException ex) {
        log.warn("Database unavailable (transient): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "10")
            .body(new ErrorResponse("WRITE_TEMPORARILY_UNAVAILABLE", "Database temporarily unavailable, please retry"));
    }

    /**
     * Handle Redis being briefly unreachable during authentication (failover, network blip, maintenance).
     *
     * <p>The auth path touches Redis for session/token lookups. When Redis is unreachable, Spring Data
     * surfaces a RedisConnectionFailureException. This is more specific than the DB
     * DataAccessResourceFailureException below (it is a subclass of DataAccessResourceFailureException),
     * so Spring dispatches Redis-connection failures here by type specificity. Transient and
     * self-healing — deliberately NOT sent to Sentry for the same reason as the DB handler: it is
     * expected during a Redis outage and would otherwise alert-storm.</p>
     */
    @ExceptionHandler(org.springframework.data.redis.RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisUnavailable(
            org.springframework.data.redis.RedisConnectionFailureException ex) {
        log.warn("Redis unavailable during authentication (transient): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "10")
            .body(new ErrorResponse("AUTH_TEMPORARILY_UNAVAILABLE", "Authentication service temporarily unavailable, please retry"));
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No handler found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", "Resource not found"));
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
