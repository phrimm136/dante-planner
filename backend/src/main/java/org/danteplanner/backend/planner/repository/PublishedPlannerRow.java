package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.api.UserProfile;

public record PublishedPlannerRow(Planner planner, UserProfile author) {
}
