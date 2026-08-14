package org.danteplanner.backend.planner.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The audit reads behind planner drift reconciliation: each query returns the rows of one audited
 * dimension — counters, catalog membership, the filter indexes, the derived recommended flag —
 * and leaves the comparison and the reporting to its caller.
 *
 * <p>Moderation is joined outer throughout: a planner with no moderation row is nothing-hidden and
 * not-taken-down, the same reading the catalog write side and {@link RecommendedSql} take. An inner
 * join would drop exactly those planners from every audit, which is the state most in need of
 * one.</p>
 *
 * <p>Naming convention within this class. A query method is named for the row set it returns and the
 * predicate that selects it, never for the audit that consumes it: {@code drifted*} for rows whose
 * stored value disagrees with a recomputation, {@code visible*} for rows passing the visibility
 * predicate the catalog is built on, {@code *Entries} for a whole index read unfiltered, and an
 * {@code xWithoutY} phrase for a set difference between two of those. Each {@code *Row} component
 * name mirrors its SQL column alias exactly, so the SELECT list and the mapper read as one list and
 * an alias rename is a component rename — which is why the two counter queries alias their columns
 * to the shared record's shape rather than to the columns they read.</p>
 */
@Repository
public class PlannerDriftAuditRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    // Build over the @Primary (routing) datasource rather than an autoconfigured JdbcTemplate,
    // which backs off when multiple datasources are present. The audit reads under the caller's
    // read-only transaction, so the routing datasource sends it to the replica. Mirrors
    // PlannerViewRepositoryImpl.
    public PlannerDriftAuditRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(this.jdbc);
    }

    /**
     * One counter row whose stored value disagrees with a recount of its authoritative child rows.
     */
    public record CounterDriftRow(UUID plannerId, long counter, long recounted) {
    }

    /**
     * One catalog row's stored recommended flag beside the flag derived from current state.
     */
    public record RecommendedDriftRow(UUID plannerId, boolean recommended, boolean derived) {
    }

    /**
     * One visible planner's stored content document and keyword column, as written.
     */
    public record ContentDocumentRow(UUID plannerId, String content, String selectedKeywords) {
    }

    /**
     * One catalog scalar copy that disagrees with the content row it was copied from.
     */
    public record CatalogScalarDriftRow(UUID plannerId, String field, String expected, String actual) {
    }

    /**
     * One catalog row's stored keyword array beside its content row's, both as written.
     */
    public record CatalogKeywordRow(UUID plannerId, String catalogKeywords, String contentKeywords) {
    }

    /**
     * One indexed entity reference.
     */
    public record EntityFilterRow(UUID plannerId, String entityType, int entityId) {
    }

    /**
     * One indexed keyword.
     */
    public record KeywordFilterRow(UUID plannerId, String keyword) {
    }

    /**
     * Stat rows whose upvote counter disagrees with the vote rows.
     *
     * @return one row per disagreeing planner
     */
    public List<CounterDriftRow> driftedUpvoteCounters() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(s.planner_id) AS planner_id, s.upvotes AS counter,
                       COUNT(v.planner_id) AS recounted
                FROM planner_stats s
                LEFT JOIN planner_votes v ON v.planner_id = s.planner_id
                GROUP BY s.planner_id, s.upvotes
                HAVING s.upvotes <> recounted
                """,
                (rs, rowNum) -> new CounterDriftRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getLong("counter"), rs.getLong("recounted")));
    }

    /**
     * Stat rows whose comment counter disagrees with the live comment rows.
     *
     * @return one row per disagreeing planner
     */
    public List<CounterDriftRow> driftedCommentCounters() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(s.planner_id) AS planner_id, s.comment_count AS counter,
                       COUNT(c.planner_id) AS recounted
                FROM planner_stats s
                LEFT JOIN planner_comments c ON c.planner_id = s.planner_id AND c.deleted_at IS NULL
                GROUP BY s.planner_id, s.comment_count
                HAVING s.comment_count <> recounted
                """,
                (rs, rowNum) -> new CounterDriftRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getLong("counter"), rs.getLong("recounted")));
    }

    /**
     * Planners that are visible on every state the catalog consults, yet carry no catalog row.
     *
     * @return the planner ids
     */
    public List<UUID> visiblePlannersWithoutCatalogRow() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(p.id) AS planner_id
                FROM planner p
                JOIN planner_content c ON c.planner_id = p.id
                JOIN planner_publication pub ON pub.planner_id = p.id
                LEFT JOIN planner_moderation m ON m.planner_id = p.id
                LEFT JOIN planner_catalog cat ON cat.planner_id = p.id
                WHERE pub.published = TRUE AND c.deleted_at IS NULL
                  AND m.taken_down_at IS NULL AND cat.planner_id IS NULL
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("planner_id")));
    }

    /**
     * Catalog rows whose planner is no longer visible.
     *
     * @return the planner ids
     */
    public List<UUID> catalogRowsWithoutVisiblePlanner() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id
                FROM planner_catalog cat
                LEFT JOIN planner_content c ON c.planner_id = cat.planner_id
                LEFT JOIN planner_publication pub ON pub.planner_id = cat.planner_id
                LEFT JOIN planner_moderation m ON m.planner_id = cat.planner_id
                WHERE pub.planner_id IS NULL OR pub.published = FALSE
                   OR c.deleted_at IS NOT NULL OR m.taken_down_at IS NOT NULL
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("planner_id")));
    }

    /**
     * Catalog scalar copies that disagree with the content row they were copied from, one row per
     * disagreeing column.
     *
     * @return one row per disagreeing catalog column
     */
    public List<CatalogScalarDriftRow> driftedCatalogScalars() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id, 'title' AS field,
                       c.title AS expected, cat.title AS actual
                FROM planner_catalog cat
                JOIN planner_content c ON c.planner_id = cat.planner_id
                WHERE NOT (cat.title <=> c.title)
                UNION ALL
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id, 'category' AS field,
                       c.category AS expected, cat.category AS actual
                FROM planner_catalog cat
                JOIN planner_content c ON c.planner_id = cat.planner_id
                WHERE NOT (cat.category <=> c.category)
                """,
                (rs, rowNum) -> new CatalogScalarDriftRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("field"), rs.getString("expected"), rs.getString("actual")));
    }

    /**
     * Both stored keyword arrays of every catalogued planner, raw.
     *
     * <p>Deliberately unfiltered where the scalar copies are compared in SQL: the stored array is
     * order-bearing and the converter sorts on write, so a SQL comparison reports drift on a row
     * whose keyword set is identical. Normalization is the caller's, through the path the runtime
     * reads these columns with.</p>
     *
     * @return one row per catalogued planner
     */
    public List<CatalogKeywordRow> catalogKeywordPairs() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id,
                       cat.selected_keywords AS catalog_keywords,
                       c.selected_keywords AS content_keywords
                FROM planner_catalog cat
                JOIN planner_content c ON c.planner_id = cat.planner_id
                """,
                (rs, rowNum) -> new CatalogKeywordRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("catalog_keywords"), rs.getString("content_keywords")));
    }

    /**
     * The stored content and keyword columns of every visible planner, for a rebuild of the filter
     * indexes.
     *
     * @return one row per visible planner
     */
    public List<ContentDocumentRow> visibleContentDocuments() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(c.planner_id) AS planner_id, c.content, c.selected_keywords
                FROM planner_content c
                JOIN planner_publication pub ON pub.planner_id = c.planner_id
                LEFT JOIN planner_moderation m ON m.planner_id = c.planner_id
                WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
                """,
                (rs, rowNum) -> new ContentDocumentRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("content"), rs.getString("selected_keywords")));
    }

    /**
     * Every row of the entity filter index.
     *
     * @return one row per indexed entity reference
     */
    public List<EntityFilterRow> entityFilterEntries() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(planner_id) AS planner_id, entity_type, entity_id
                FROM planner_entity_filter
                """,
                (rs, rowNum) -> new EntityFilterRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("entity_type"), rs.getInt("entity_id")));
    }

    /**
     * Every row of the keyword filter index.
     *
     * @return one row per indexed keyword
     */
    public List<KeywordFilterRow> keywordFilterEntries() {
        return jdbc.query(
                "SELECT BIN_TO_UUID(planner_id) AS planner_id, keyword FROM planner_keyword_filter",
                (rs, rowNum) -> new KeywordFilterRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("keyword")));
    }

    /**
     * Catalog rows whose stored recommended flag disagrees with the derived one.
     *
     * @param threshold upvotes at which a planner counts as recommended
     * @return one row per disagreeing catalog row
     */
    public List<RecommendedDriftRow> driftedRecommendedFlags(int threshold) {
        return namedJdbc.query(RecommendedSql.DRIFTED_ROWS, Map.of("threshold", threshold),
                (rs, rowNum) -> new RecommendedDriftRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getBoolean("recommended"), rs.getBoolean("derived")));
    }
}
