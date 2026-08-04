package org.danteplanner.backend.shared.sse;

/**
 * The payload of an account-suspension SSE event.
 *
 * <p>A missing reason and a missing duration reach the client as an empty string and a zero
 * rather than as null: the browser reads both fields unconditionally.</p>
 *
 * @param suspensionType  the kind of suspension, {@code BAN} or {@code TIMEOUT}
 * @param reason          the reason given, empty when none was
 * @param durationMinutes the timeout duration, zero for a ban
 */
public record AccountSuspendedPayload(
        SuspensionType suspensionType, String reason, int durationMinutes) {

    /**
     * Builds the payload, substituting the client-facing defaults for an absent reason or
     * duration.
     *
     * @param suspensionType  the kind of suspension
     * @param reason          the reason given, or null
     * @param durationMinutes the timeout duration, or null for a ban
     * @return the payload
     */
    public static AccountSuspendedPayload of(
            SuspensionType suspensionType, String reason, Integer durationMinutes) {
        return new AccountSuspendedPayload(
                suspensionType,
                reason != null ? reason : "",
                durationMinutes != null ? durationMinutes : 0);
    }
}
