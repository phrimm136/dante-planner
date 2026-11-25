package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

@Component
public class PlannerLimitValidator {

    @Parsed
    public record Room(Long userId) {

        private static Room parse(Long userId, long currentCount, int limit) {
            if (currentCount + 1 > limit) {
                throw new PlannerLimitExceededException(currentCount, limit);
            }
            return new Room(userId);
        }
    }

    public Room requireRoom(Long userId, long currentCount, int limit) {
        return Room.parse(userId, currentCount, limit);
    }
}
