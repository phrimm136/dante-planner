package org.danteplanner.backend.dto.planner;

/**
 * Internal result wrapper for upsert operations.
 *
 * <p>Used by controller to determine HTTP status code:
 * - created = true → 201 Created
 * - created = false → 200 OK
 *
 * <p>Not exposed in API - controller extracts response for client.
 */
public record UpsertResult(PlannerResponse response, boolean created) {

    /**
     * Create result for a newly created planner.
     */
    public static UpsertResult created(PlannerResponse response) {
        return new UpsertResult(response, true);
    }

    /**
     * Create result for an updated planner.
     */
    public static UpsertResult updated(PlannerResponse response) {
        return new UpsertResult(response, false);
    }
}
