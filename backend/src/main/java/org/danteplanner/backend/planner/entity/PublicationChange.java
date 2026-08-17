package org.danteplanner.backend.planner.entity;

/**
 * What a publication-state transition turned out to be.
 *
 * <p>The aggregate decides; callers consume. Nothing outside {@link PlannerPublication} re-derives
 * whether a publish was the planner's first, or whether a request changed anything at all.</p>
 */
public enum PublicationChange {

    /** The planner already held the requested state. */
    NONE,

    /** The planner became published for the first time. */
    FIRST_PUBLISH,

    /** The planner became published again, its first-publish stamp already set. */
    REPUBLISH,

    /** The planner left public view. */
    WITHDRAWN;

    /**
     * @return whether the transition changed the planner
     */
    public boolean changed() {
        return this != NONE;
    }
}
