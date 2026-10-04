package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import lombok.Builder;
import org.danteplanner.backend.user.api.UserSummary;

@Builder
public record TimeoutResponse(
    String usernameSuffix,
    Instant timeoutUntil,
    String message
) {

    public static TimeoutResponse fromUser(UserSummary user) {
        return TimeoutResponse.builder()
                .usernameSuffix(user.usernameSuffix())
                .timeoutUntil(user.timeoutUntil())
                .build();
    }
}
