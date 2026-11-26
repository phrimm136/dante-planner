package org.danteplanner.backend.planner.repository;

public final class VisibilitySql {

    public static final String VISIBILITY = "CASE WHEN pub.published = TRUE AND a.deletedAt IS NULL"
            + " THEN org.danteplanner.backend.planner.api.Visibility.SHOWN"
            + " ELSE org.danteplanner.backend.planner.api.Visibility.HIDDEN END";

    private VisibilitySql() {
    }
}
