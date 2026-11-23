package org.danteplanner.backend.moderation.event;

import org.danteplanner.backend.shared.sse.SuspensionType;

public record AccountSuspendedEvent(
        Long userId,
        String reason,
        SuspensionType suspensionType,
        int durationMinutes) {

    private static final int BAN_DURATION_MINUTES = 0;

    public static AccountSuspendedEvent ban(Long userId, String reason) {
        return new AccountSuspendedEvent(userId, reason, SuspensionType.BAN, BAN_DURATION_MINUTES);
    }
}
