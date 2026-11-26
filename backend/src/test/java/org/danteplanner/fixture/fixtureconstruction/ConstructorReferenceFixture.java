package org.danteplanner.fixture.fixtureconstruction;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Takedown;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.user.entity.User;

public class ConstructorReferenceFixture {

    interface PlannerConstructor {
        StoredPlanner of(UUID id, Long userId, PlannerType plannerType, Instant createdAt, StoredContent content,
                Publication publication, NotificationSetting ownerNotifications, Listing listing, Takedown takedown);
    }

    static PlannerConstructor planner() {
        return StoredPlanner::new;
    }

    static Supplier<User.UserBuilder> user() {
        return User::builder;
    }
}
