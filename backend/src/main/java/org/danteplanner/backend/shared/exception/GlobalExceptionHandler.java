package org.danteplanner.backend.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserTimedOutException;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.auth.exception.TokenRevokedException;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final CookieUtils cookieUtils;
    private final ObjectMapper objectMapper;

    public record ErrorResponse(String code, String message) {}

    public record ConflictErrorResponse(String code, String message, Long serverVersion) {}

    private static HttpStatus statusOf(ErrorKind kind) {
        return switch (kind) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
        };
    }

    /**
     * Answers every business error whose response is fully described by its kind, code, and
     * message. A subclass needing more of the response than that keeps its own handler below,
     * which Spring prefers over this one by exception-type specificity.
     *
     * @param ex the business error
     * @return the client-facing error response
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        log.warn("{}: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(statusOf(ex.getKind()))
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
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
        cookieUtils.clearAuthCookies(response);
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

    /**
     * A bare {@link IllegalArgumentException} states an invariant the caller cannot influence, so
     * reaching here is a bug. Its message may name internals and never reaches the client; a
     * rejection the caller can act on throws {@link InvalidRequestException} instead.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument reached the handler", ex);
        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
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

    /**
     * Rate limiting is an expected user error (no Sentry). The response is written directly to
     * bypass content negotiation: SSE endpoints declare {@code produces=text/event-stream}, for
     * which no converter can serialize the JSON body — returning a {@code ResponseEntity} there
     * throws {@code HttpMediaTypeNotAcceptableException} and the 429 escapes to Tomcat as a 500.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public void handleRateLimitExceeded(RateLimitExceededException ex, HttpServletResponse response) throws IOException {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage())));
    }

    /**
     * A full planner SSE registry is an expected user error (no Sentry). The response is written
     * directly because the subscription endpoint declares {@code produces=text/event-stream}, for
     * which no converter can serialize the JSON body.
     */
    @ExceptionHandler(SseCapacityExceededException.class)
    public void handleSseCapacityExceeded(SseCapacityExceededException ex, HttpServletResponse response) throws IOException {
        log.warn("SSE capacity exceeded: {}", ex.getMessage());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
            new ErrorResponse("SSE_CAPACITY_EXCEEDED", "Too many active connections for this planner")));
    }

    @ExceptionHandler(PlannerConflictException.class)
    public ResponseEntity<ConflictErrorResponse> handlePlannerConflict(PlannerConflictException ex) {
        log.warn("Planner sync conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ConflictErrorResponse("SYNC_CONFLICT", ex.getMessage(), ex.getActualVersion()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ConflictErrorResponse> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent write conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ConflictErrorResponse("CONCURRENT_WRITE", "The resource was modified concurrently", null));
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
    private static final Set<String> USER_FACING_ERROR_CODES = Stream.of(
            ErrorCode.EMPTY_CONTENT,
            ErrorCode.SIZE_EXCEEDED,
            ErrorCode.MALFORMED_JSON)
            .map(ErrorCode::getCode)
            .collect(Collectors.toUnmodifiableSet());

    @ExceptionHandler(PlannerValidationException.class)
    public ResponseEntity<ErrorResponse> handlePlannerValidation(PlannerValidationException ex) {
        if (USER_FACING_ERROR_CODES.contains(ex.getErrorCode())) {
            logValidationError(ex.getErrorCode(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
        }

        // Each sub-error is logged on its own line so CloudWatch can search for one code.
        if (ex.getSubErrors().isEmpty()) {
            logValidationError(ex.getErrorCode(), ex.getMessage());
        } else {
            ex.getSubErrors().forEach(e -> logValidationError(e.code(), e.message()));
        }

        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", "Invalid planner content structure"));
    }

    private static void logValidationError(String code, String message) {
        log.warn("Planner validation error [{}]: {}", code, message);
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
     * <p>{@link ConstraintViolationClassifier} decides the outcome from typed driver signals and the
     * {@link KnownConstraint} table; this method only renders it. Expected races (a UUID collision, a
     * repeated user action) return 409 without an alert; anything else is a defect and reaches
     * Sentry.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        ConstraintViolationOutcome outcome = ConstraintViolationClassifier.classify(ex);

        switch (outcome) {
            case UUID_COLLISION -> log.warn("UUID collision detected (race condition): {}", ex.getMessage());
            case DUPLICATE_ACTION -> log.warn("Duplicate action bypassed application check: {}", ex.getMessage());
            case UNEXPECTED_CONFLICT -> log.warn("Unexpected unique constraint violation: {}", ex.getMessage());
            case INVALID_DATA -> log.error("Database constraint violation", ex);
        }

        if (outcome.reportToSentry()) {
            Sentry.captureException(ex);
        }

        return ResponseEntity.status(outcome.status())
            .body(new ErrorResponse(outcome.code(), outcome.clientMessage()));
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ErrorResponse> handleCannotAcquireLock(CannotAcquireLockException ex) {
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
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(
            NestedRuntimeException ex) {
        log.warn("Database unavailable (transient): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "10")
            .body(new ErrorResponse(
                DegradationErrorConstants.DB_UNAVAILABLE_CODE, DegradationErrorConstants.DB_UNAVAILABLE_MESSAGE));
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
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisUnavailable(
            RedisConnectionFailureException ex) {
        log.warn("Redis unavailable during authentication (transient): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "10")
            .body(new ErrorResponse(
                DegradationErrorConstants.AUTH_UNAVAILABLE_CODE, DegradationErrorConstants.AUTH_UNAVAILABLE_MESSAGE));
    }

    /**
     * Handle the rate-limit Redis being briefly unreachable or slow (failover, network blip, maintenance).
     *
     * <p>The rate limiter uses a RAW Lettuce client (only {@code RedisConnectionConfig} does — bucket4j's
     * {@code LettuceBasedProxyManager} is handed a raw {@code RedisClient.connect(...)}), so a rate-limit
     * Redis outage does NOT surface as Spring Data's {@code RedisConnectionFailureException}. It rethrows
     * the raw {@code RedisException} (RedisConnectionException / RedisCommandTimeoutException /
     * RedisSystemException) unwrapped. Mapping the common supertype covers every cut variant. Transient and
     * self-healing — the client reconnects when Redis returns.</p>
     *
     * <p>Returning 503 (not letting it fall to the catch-all 500) honours the edge contract: nginx has
     * {@code proxy_intercept_errors on}, so it rewrites any backend 5xx to {@code BACKEND_UNAVAILABLE}.
     * A 500 would leak through as a raw INTERNAL_ERROR. Deliberately NOT sent to Sentry, for the same reason
     * as the DB and auth-Redis handlers: it is expected during a Redis outage and would otherwise alert-storm.
     * The {@code RATE_LIMIT_TEMPORARILY_UNAVAILABLE} code is internal-only; external clients see
     * BACKEND_UNAVAILABLE.</p>
     */
    @ExceptionHandler(RedisException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitRedisUnavailable(RedisException ex) {
        log.warn("Rate-limit Redis unavailable (transient): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "10")
            .body(new ErrorResponse(
                DegradationErrorConstants.RATE_LIMIT_UNAVAILABLE_CODE, DegradationErrorConstants.RATE_LIMIT_UNAVAILABLE_MESSAGE));
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
     *
     * <p>Any other IOException is a server failure and answers 500 — unless the response is
     * already committed, where a null return marks the request handled because no status or body
     * can still be written.</p>
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException ex, HttpServletResponse response) {
        if (ex instanceof ClientAbortException || carriesDisconnectStrerror(ex)) {
            log.debug("SSE client disconnected ({}): {}", ex.getClass().getName(), ex.getMessage());
            return null;
        }

        // Other IOExceptions are unexpected and should be sent to Sentry
        Sentry.captureException(ex);
        log.error("Unexpected IOException", ex);

        if (response.isCommitted()) {
            return null;
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    /**
     * Socket teardown reaches the JVM as a plain {@link IOException} carrying the OS strerror text
     * and nothing else — no subclass, no code — so these phrases are the only available signal.
     * {@code Locale.ROOT} keeps the default locale out of the decision.
     */
    private static final List<String> CLIENT_DISCONNECT_STRERRORS = List.of(
            "broken pipe",
            "connection reset",
            "connection abort",
            "stream closed");

    private static boolean carriesDisconnectStrerror(IOException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        return CLIENT_DISCONNECT_STRERRORS.stream().anyMatch(message::contains);
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
}
