package org.danteplanner.backend.moderation.event;

import org.danteplanner.backend.shared.sse.SuspensionType;

/**
 * A restriction that committed and still has to reach the restricted user's open sessions.
 *
 * @param userId          the restricted user
 * @param reason          the moderator's stated reason, shown to the user
 * @param suspensionType  whether the restriction is a timeout or a ban
 * @param durationMinutes how long a timeout lasts; null for a ban
 */
public record AccountSuspendedEvent(
        Long userId,
        String reason,
        SuspensionType suspensionType,
        Integer durationMinutes) {

    /**
     * A ban reaching the banned user's open sessions. A ban is permanent, so it carries no duration.
     *
     * @param userId the banned user
     * @param reason the moderator's stated reason, shown to the user
     * @return the event
     */
    public static AccountSuspendedEvent ban(Long userId, String reason) {
        return new AccountSuspendedEvent(userId, reason, SuspensionType.BAN, null);
    }
}
