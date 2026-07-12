package org.danteplanner.backend.shared.exception;

/**
 * Shared (code, message) pairs for the transient-degradation 503 responses.
 *
 * <p>Consumed by both {@link GlobalExceptionHandler} (its in-DispatcherServlet handlers) and the
 * pre-dispatch {@code JwtAuthenticationFilter}. The two mapping sites cannot be merged — the filter
 * runs before the DispatcherServlet, so {@code @RestControllerAdvice} cannot catch failures the
 * filter throws — so these constants keep the emitted values from drifting between the two sites.</p>
 */
public final class DegradationErrorConstants {

    public static final String DB_UNAVAILABLE_CODE = "WRITE_TEMPORARILY_UNAVAILABLE";
    public static final String DB_UNAVAILABLE_MESSAGE = "Database temporarily unavailable, please retry";

    public static final String AUTH_UNAVAILABLE_CODE = "AUTH_TEMPORARILY_UNAVAILABLE";
    public static final String AUTH_UNAVAILABLE_MESSAGE =
            "Authentication service temporarily unavailable, please retry";

    public static final String RATE_LIMIT_UNAVAILABLE_CODE = "RATE_LIMIT_TEMPORARILY_UNAVAILABLE";
    public static final String RATE_LIMIT_UNAVAILABLE_MESSAGE = "Rate limiter temporarily unavailable, please retry";

    private DegradationErrorConstants() {
    }
}
