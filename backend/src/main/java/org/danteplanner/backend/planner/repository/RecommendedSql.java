package org.danteplanner.backend.planner.repository;

/**
 * The one spelling of "this planner is recommendable", plus the statements derived from it.
 *
 * <p>The predicate binds two aliases that {@link #LEFT_JOINS} supplies, both outer-joined onto a
 * driving row aliased {@code c} carrying {@code planner_id}. Both columns are read through
 * {@code COALESCE}, so a planner with no counter row and no moderation row is answered rather than
 * dropped: an absent moderation row means nothing is hidden.</p>
 */
public final class RecommendedSql {

    /**
     * Outer joins supplying the {@code s} (counters) and {@code m} (moderation) aliases the
     * predicate reads, keyed off a driving row aliased {@code c}.
     */
    public static final String LEFT_JOINS =
            " LEFT JOIN planner_stats s ON s.planner_id = c.planner_id"
                    + " LEFT JOIN planner_moderation m ON m.planner_id = c.planner_id";

    /**
     * Boolean expression over {@link #LEFT_JOINS}' aliases and a {@code :threshold} parameter.
     */
    public static final String PREDICATE =
            "(COALESCE(s.upvotes, 0) >= :threshold"
                    + " AND COALESCE(m.hidden_from_recommended, FALSE) = FALSE)";

    /**
     * Recompute one catalog row's derived flag. Row-scoped; a no-op while the planner has no
     * catalog row.
     */
    public static final String REFRESH_RECOMMENDED =
            "UPDATE planner_catalog c" + LEFT_JOINS
                    + " SET c.recommended = " + PREDICATE
                    + " WHERE c.planner_id = :id";

    /**
     * Re-list an owner's planners that are still visible on their own terms, deriving the flag for
     * each. Taken-down planners are excluded: a moderator withdrawal must not be undone by the
     * owner returning.
     */
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

    /**
     * Catalog rows whose stored flag disagrees with the derived one.
     */
    public static final String DRIFTED_ROWS =
            "SELECT BIN_TO_UUID(c.planner_id) AS planner_id, c.recommended, "
                    + PREDICATE + " AS derived"
                    + " FROM planner_catalog c" + LEFT_JOINS
                    + " HAVING c.recommended <> derived";

    private RecommendedSql() {
    }

    /**
     * The in-memory mirror of {@link #PREDICATE}, for the publish path that derives the flag from
     * an aggregate it already holds.
     *
     * @param upvotes                the planner's current upvote count
     * @param hiddenFromRecommended  the moderation flag, null when the planner has no moderation row
     * @param threshold              upvotes at which a planner counts as recommended
     * @return whether the planner is recommendable
     */
    public static boolean isRecommended(int upvotes, Boolean hiddenFromRecommended, int threshold) {
        return upvotes >= threshold && !Boolean.TRUE.equals(hiddenFromRecommended);
    }
}
