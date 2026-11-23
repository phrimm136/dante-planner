package org.danteplanner.backend.planner.entity;

import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Stamps a {@link PlannerContent} for fixtures that never reach a persistence provider.
 *
 * <p>JPA auditing fills {@code lastModifiedAt} only on a real save, while its column forbids null
 * and readers of a stored row take it for granted.</p>
 */
public final class PlannerContentLifecycle {

    private static final Instant STAMP = Instant.parse("2026-01-01T00:00:00Z");

    private PlannerContentLifecycle() {
    }

    /**
     * Gives the row the modification stamp a stored row always carries.
     *
     * @param content the content row to stamp
     * @return the same row
     */
    public static PlannerContent asPersisted(PlannerContent content) {
        ReflectionTestUtils.setField(content, "lastModifiedAt", STAMP);
        return content;
    }

    /**
     * Stamps an aggregate's content row, for a mocked repository standing in for the save that
     * would have filled it.
     *
     * @param planner the aggregate whose content row to stamp
     * @return the same aggregate
     */
    public static Planner asPersisted(Planner planner) {
        asPersisted(planner.getContent());
        return planner;
    }
}
