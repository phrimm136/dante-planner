package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.user.api.UserSummary;
import org.springframework.stereotype.Component;

@Component
public class PlannerLimitValidator {

    @Parsed
    public record Room(Long userId) {

        private static Room parse(UserSummary owner, long currentCount, int limit) {
            if (currentCount + 1 > limit) {
                throw new PlannerLimitExceededException(currentCount, limit);
            }
            return new Room(owner.id());
        }
    }

    public Room requireRoom(UserSummary owner, long currentCount, int limit) {
        return Room.parse(owner, currentCount, limit);
    }
}
