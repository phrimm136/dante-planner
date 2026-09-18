package org.danteplanner.backend.planner.dto;

public record UpsertResult(PlannerResponse response, boolean isCreated) {

    public static UpsertResult created(PlannerResponse response) {
        return new UpsertResult(response, true);
    }

    public static UpsertResult updated(PlannerResponse response) {
        return new UpsertResult(response, false);
    }
}
