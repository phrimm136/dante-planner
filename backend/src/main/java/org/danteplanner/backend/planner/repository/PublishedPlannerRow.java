package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.api.Visibility;
import org.danteplanner.backend.user.api.UserProfile;

public record PublishedPlannerRow(StoredPlanner planner, UserProfile author, Visibility visibility) {
}
