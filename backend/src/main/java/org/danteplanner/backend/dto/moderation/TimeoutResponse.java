package org.danteplanner.backend.dto.moderation;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.User;

/**
 * Response DTO containing user timeout information.
 *
 * <p>Used by ModerationController to return timeout status after
 * timeout operations. Uses username suffix instead of internal ID for privacy.</p>
 */
@Data
@Builder
public class TimeoutResponse {

    /**
     * The user's username suffix (public identifier).
     */
    private String usernameSuffix;

    /**
     * The timestamp until which the user is timed out.
     * Null if the user is not currently timed out.
     */
    private Instant timeoutUntil;

    /**
     * A human-readable message describing the operation result.
     */
    private String message;

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
