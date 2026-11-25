package org.danteplanner.backend.user.validation;

import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.user.api.UserNotFoundException;

@Parsed
public record WrittenAccount(Long userId) {

    public static WrittenAccount of(Long userId, WriteOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> new WrittenAccount(userId);
            case NOTHING -> throw new UserNotFoundException(userId);
        };
    }
}
