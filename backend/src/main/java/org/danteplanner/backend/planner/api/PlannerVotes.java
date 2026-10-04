package org.danteplanner.backend.planner.api;

import java.util.UUID;

public interface PlannerVotes {

    void requireFirstVote(boolean alreadyVoted, UUID plannerId, Long userId);
}
