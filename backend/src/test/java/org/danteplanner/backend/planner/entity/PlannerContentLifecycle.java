package org.danteplanner.backend.planner.entity;

/**
 * Drives {@link PlannerContent}'s JPA lifecycle callbacks for fixtures that never reach a
 * persistence provider.
 *
 * <p>Lives in the entity package because the callbacks are protected, and exists at all because a
 * content row that skipped {@code @PrePersist} carries no digest, which every mapper reading one
 * treats as a fault.</p>
 */
public final class PlannerContentLifecycle {

    private PlannerContentLifecycle() {
    }

    /**
     * Applies the persist callback, giving the row the digest a stored row always carries.
     *
     * @param content the content row to stamp
     * @return the same row
     */
    public static PlannerContent asPersisted(PlannerContent content) {
        content.onCreate();
        return content;
    }

    /**
     * Applies the persist callback to an aggregate's content row, for a mocked repository standing
     * in for the save that would have fired it.
     *
     * @param planner the aggregate whose content row to stamp
     * @return the same aggregate
     */
    public static Planner asPersisted(Planner planner) {
        asPersisted(planner.getContent());
        return planner;
    }
}
