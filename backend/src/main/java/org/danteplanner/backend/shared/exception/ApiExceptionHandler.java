package org.danteplanner.backend.shared.exception;

import io.lettuce.core.RedisException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String NOT_FOUND_CODE = "NOT_FOUND";
    private static final String NOT_FOUND_DETAIL = "Resource not found";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred";
    private static final String DEADLOCK_CODE = "DEADLOCK";
    private static final String DEADLOCK_DETAIL = "Database temporarily busy, please retry";
    private static final String CONCURRENT_WRITE_CODE = "CONCURRENT_WRITE";
    private static final String CONCURRENT_WRITE_DETAIL = "The resource was modified concurrently";

    private final CookieUtils cookieUtils;

    /**
     * The single rendering path. Clearing the matched endpoint's producible media types is what
     * keeps an event-stream endpoint's 4xx from being negotiated as {@code text/event-stream},
     * for which no converter can write a body.
     */
    private ResponseEntity<Object> respond(
            Exception ex, ProblemDetail body, HttpHeaders headers, WebRequest request) {
        return handleExceptionInternal(ex, body, headers, HttpStatus.valueOf(body.getStatus()), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        request.removeAttribute(
                HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

        if (ex instanceof DomainException domain) {
            log.warn("{}", domain.getMessage());
            if (domain.reportable()) {
                Sentry.captureException(domain);
            }
        } else if (ex instanceof SseCapacityExceededException) {
            log.warn("{}", ex.getMessage());
        }

        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        Class<?> requiredType = ex.getRequiredType();

        if (UUID.class.equals(requiredType)) {
            log.warn("Invalid UUID format for parameter '{}': {}", name, ex.getValue());
            return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.NOT_FOUND),
                    NOT_FOUND_CODE, NOT_FOUND_DETAIL), headers, request);
        }

        log.warn("Type mismatch for parameter '{}': expected {}, got {}",
                name, requiredType != null ? requiredType.getSimpleName() : "unknown", ex.getValue());
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
                VALIDATION_ERROR_CODE, "Invalid parameter format"), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {} | body: {}", detail, ex.getBindingResult().getTarget());
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
                VALIDATION_ERROR_CODE, detail), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        log.warn("Unreadable request body");
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
                VALIDATION_ERROR_CODE, "Invalid request body"), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        log.debug("No handler found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.NOT_FOUND),
                NOT_FOUND_CODE, NOT_FOUND_DETAIL), headers, request);
    }

    /**
     * The one owned error with its own handler: the response carries a cookie clear that no body
     * can express.
     */
    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<Object> handleSessionRevoked(
            SessionRevokedException ex, HttpServletResponse response, WebRequest request) {
        cookieUtils.clearAuthCookies(response);
        return respond(ex, ex.getBody(), ex.getHeaders(), request);
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
    public ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {

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

        return respond(ex, Problems.fill(ProblemDetail.forStatus(outcome.status()),
                outcome.code(), outcome.clientMessage()), new HttpHeaders(), request);
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<Object> handleCannotAcquireLock(CannotAcquireLockException ex, WebRequest request) {
        log.warn("Database deadlock detected: {}", ex.getMessage());
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                DEADLOCK_CODE, DEADLOCK_DETAIL), new HttpHeaders(), request);
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
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<Object> handleDatabaseUnavailable(NestedRuntimeException ex, WebRequest request) {
        log.warn("Database unavailable (transient): {}", ex.getMessage());
        return degraded(ex, DegradationErrorConstants.DB_UNAVAILABLE, request);
    }

    /**
     * Handle Redis being briefly unreachable during authentication (failover, network blip, maintenance).
     *
     * <p>The auth path touches Redis for session/token lookups. When Redis is unreachable, Spring Data
     * surfaces a RedisConnectionFailureException. This is more specific than the DB
     * DataAccessResourceFailureException above (it is a subclass of DataAccessResourceFailureException),
     * so Spring dispatches Redis-connection failures here by type specificity. Transient and
     * self-healing — deliberately NOT sent to Sentry for the same reason as the DB handler: it is
     * expected during a Redis outage and would otherwise alert-storm.</p>
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Object> handleRedisUnavailable(
            RedisConnectionFailureException ex, WebRequest request) {
        log.warn("Redis unavailable during authentication (transient): {}", ex.getMessage());
        return degraded(ex, DegradationErrorConstants.AUTH_UNAVAILABLE, request);
    }

    /**
     * Handle the rate-limit Redis being briefly unreachable or slow (failover, network blip, maintenance).
     *
     * <p>The rate limiter uses a RAW Lettuce client (only {@code RedisConnectionConfig} does — bucket4j's
     * {@code LettuceBasedProxyManager} is handed a raw {@code RedisClient.connect(...)}), so a rate-limit
     * Redis outage does NOT surface as Spring Data's {@code RedisConnectionFailureException}. It rethrows
     * the raw {@code RedisException} (RedisConnectionException / RedisCommandTimeoutException /
     * RedisSystemException) unwrapped — or bucket4j's own {@code TimeoutException} when its request
     * timeout fires before Lettuce's equal command timeout. Mapping both covers every cut variant.
     * Transient and self-healing — the client reconnects when Redis returns. Deliberately NOT sent to
     * Sentry, for the same reason as the DB and auth-Redis handlers: it is expected during a Redis
     * outage and would otherwise alert-storm.</p>
     */
    @ExceptionHandler({RedisException.class, io.github.bucket4j.TimeoutException.class})
    public ResponseEntity<Object> handleRateLimitRedisUnavailable(RuntimeException ex, WebRequest request) {
        log.warn("Rate-limit Redis unavailable (transient): {}", ex.getMessage());
        return degraded(ex, DegradationErrorConstants.RATE_LIMIT_UNAVAILABLE, request);
    }

    private ResponseEntity<Object> degraded(
            Exception ex, DegradationErrorConstants.Entry entry, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, DegradationErrorConstants.RETRY_AFTER_SECONDS);
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                entry.code(), entry.message()), headers, request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex, WebRequest request) {
        log.warn("Concurrent write conflict: {}", ex.getMessage());
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.CONFLICT),
                CONCURRENT_WRITE_CODE, CONCURRENT_WRITE_DETAIL), new HttpHeaders(), request);
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
    public ResponseEntity<Object> handleIOException(
            IOException ex, HttpServletResponse response, WebRequest request) {

        if (ex instanceof ClientAbortException || carriesDisconnectStrerror(ex)) {
            log.debug("SSE client disconnected ({}): {}", ex.getClass().getName(), ex.getMessage());
            return null;
        }

        Sentry.captureException(ex);
        log.error("Unexpected IOException", ex);

        if (response.isCommitted()) {
            return null;
        }

        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR),
                INTERNAL_ERROR_CODE, INTERNAL_ERROR_DETAIL), new HttpHeaders(), request);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        Sentry.captureException(ex);
        log.error("Unexpected error", ex);
        return respond(ex, Problems.fill(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR),
                INTERNAL_ERROR_CODE, INTERNAL_ERROR_DETAIL), new HttpHeaders(), request);
    }
}
