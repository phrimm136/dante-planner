package org.danteplanner.backend.planner.repository;

public final class RecommendedSql {

    public static final String LEFT_JOINS =
            " LEFT JOIN planner_stats s ON s.planner_id = c.planner_id"
                    + " LEFT JOIN planner_moderation m ON m.planner_id = c.planner_id";

    public static final String PREDICATE =
            "(COALESCE(s.upvotes, 0) >= :threshold"
                    + " AND COALESCE(m.hidden_from_recommended, FALSE) = FALSE)";

    public static final String REFRESH_RECOMMENDED =
            "UPDATE planner_catalog c" + LEFT_JOINS
                    + " SET c.recommended = " + PREDICATE
                    + " WHERE c.planner_id = :plannerId";

    public static final String RESTORE_ALL_OWNED_BY =
            "INSERT INTO planner_catalog"
                    + " (planner_id, planner_type, category, title, selected_keywords,"
                    + " first_published_at, recommended)"
                    + " SELECT p.id, p.planner_type, c.category, c.title, c.selected_keywords,"
                    + " pub.first_published_at, " + PREDICATE
                    + " FROM planner p"
                    + " JOIN planner_content c ON c.planner_id = p.id"
                    + " JOIN planner_publication pub ON pub.planner_id = p.id"
                    + LEFT_JOINS
                    + " WHERE p.user_id = :userId"
                    + " AND pub.published = TRUE"
                    + " AND c.deleted_at IS NULL"
                    + " AND m.taken_down_at IS NULL";

    public static final String DRIFTED_ROWS =
            "SELECT BIN_TO_UUID(c.planner_id) AS planner_id, c.recommended, "
                    + PREDICATE + " AS derived"
                    + " FROM planner_catalog c" + LEFT_JOINS
                    + " HAVING c.recommended <> derived";

    private RecommendedSql() {
    }

    public static boolean isRecommended(int upvotes, boolean hiddenFromRecommended, int threshold) {
        return upvotes >= threshold && !hiddenFromRecommended;
    }
}
