package org.danteplanner.backend.shared.exception;

public final class DegradationErrorConstants {

    public static final String RETRY_AFTER_SECONDS = "10";

    public static final Entry DB_UNAVAILABLE = new Entry(
            "WRITE_TEMPORARILY_UNAVAILABLE",
            "Database temporarily unavailable, please retry");

    public static final Entry AUTH_UNAVAILABLE = new Entry(
            "AUTH_TEMPORARILY_UNAVAILABLE",
            "Authentication service temporarily unavailable, please retry");

    public static final Entry RATE_LIMIT_UNAVAILABLE = new Entry(
            "RATE_LIMIT_TEMPORARILY_UNAVAILABLE",
            "Rate limiter temporarily unavailable, please retry");

    public record Entry(String code, String message) {}

    private DegradationErrorConstants() {
    }
}
