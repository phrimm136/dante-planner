package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import lombok.Builder;
import org.danteplanner.backend.user.entity.User;

/**
 * Response DTO containing user timeout information.
 *
 * <p>Used by ModerationController to return timeout status after
 * timeout operations. Uses username suffix instead of internal ID for privacy.</p>
 *
 * @param usernameSuffix the user's username suffix (public identifier)
 * @param timeoutUntil   the timestamp until which the user is timed out (null if not timed out)
 * @param message        a human-readable message describing the operation result
 */
@Builder
public record TimeoutResponse(
    String usernameSuffix,
    Instant timeoutUntil,
    String message
) {

    /**
     * Creates a TimeoutResponse from a User entity and message.
     *
     * @param user    the user entity
     * @param message the operation result message
     * @return the response DTO
     */
    public static TimeoutResponse fromUser(User user, String message) {
        return TimeoutResponse.builder()
                .usernameSuffix(user.getUsernameSuffix())
                .timeoutUntil(user.getTimeoutUntil())
                .message(message)
                .build();
    }
}
