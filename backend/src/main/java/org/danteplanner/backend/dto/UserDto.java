package org.danteplanner.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {
    private String email;
    private String usernameEpithet;
    private String usernameSuffix;
    private String role; // NORMAL, MODERATOR, or ADMIN

    // Restriction status (null if not restricted)
    private Boolean isBanned;
    private Instant bannedAt;
    private String banReason;
    private Boolean isTimedOut;
    private Instant timeoutUntil;
    private String timeoutReason;
}
