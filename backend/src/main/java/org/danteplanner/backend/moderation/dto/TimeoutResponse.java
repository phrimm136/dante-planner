package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import lombok.Builder;
import org.danteplanner.backend.user.entity.User;

@Builder
public record TimeoutResponse(
    String usernameSuffix,
    Instant timeoutUntil,
    String message
) {

    public static TimeoutResponse fromUser(User user) {
        return fromUser(user, null);
    }

    public static TimeoutResponse fromUser(User user, String message) {
        return TimeoutResponse.builder()
                .usernameSuffix(user.getUsernameSuffix())
                .timeoutUntil(user.getTimeoutUntil())
                .message(message)
                .build();
    }
}
