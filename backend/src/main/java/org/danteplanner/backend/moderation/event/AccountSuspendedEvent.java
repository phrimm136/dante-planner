package org.danteplanner.backend.moderation.event;

import org.danteplanner.backend.shared.sse.SuspensionType;

public record AccountSuspendedEvent(
        Long userId,
        String reason,
        SuspensionType suspensionType,
        Integer durationMinutes) {

    public static AccountSuspendedEvent ban(Long userId, String reason) {
        return new AccountSuspendedEvent(userId, reason, SuspensionType.BAN, null);
    }
}
