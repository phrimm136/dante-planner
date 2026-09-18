package org.danteplanner.backend.shared.sse;

public record AccountSuspendedPayload(
        SuspensionType suspensionType, String reason, int durationMinutes) {

    public static AccountSuspendedPayload of(
            SuspensionType suspensionType, String reason, Integer durationMinutes) {
        return new AccountSuspendedPayload(
                suspensionType,
                reason != null ? reason : "",
                durationMinutes != null ? durationMinutes : 0);
    }
}
