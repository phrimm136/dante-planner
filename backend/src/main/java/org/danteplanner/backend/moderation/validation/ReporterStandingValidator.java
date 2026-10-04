package org.danteplanner.backend.moderation.validation;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReporterStandingValidator {

    private final Users users;

    public void checkNotBanned(Long userId) {
        UserSummary user = users.findById(userId);

        if (user.isBanned()) {
            throw new UserBannedException(user.id(), user.bannedAt());
        }
    }
}
