package org.danteplanner.backend.shared.sse;

public record AccountSuspendedPayload(
        SuspensionType suspensionType, String reason, int durationMinutes) {
}
