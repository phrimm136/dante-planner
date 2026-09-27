package org.danteplanner.backend.planner.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlannerDriftAuditRepository {

    private static final int RECOMMENDED_AUDIT_WINDOW_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    // Build over the @Primary (routing) datasource rather than an autoconfigured JdbcTemplate,
    // which backs off when multiple datasources are present.
    public PlannerDriftAuditRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(this.jdbc);
    }

    public record CounterDriftRow(UUID plannerId, long counter, long recounted) {
    }

    public record RecommendedDriftRow(UUID plannerId, boolean recommended, boolean derived) {
    }

    public record ContentDocumentRow(UUID plannerId, String category, String content) {
    }

    public record ContentKeywordRow(UUID plannerId, String contentKeywords, String columnKeywords) {
    }

    public record CatalogScalarDriftRow(UUID plannerId, String field, String expected, String actual) {
    }

    public record CatalogKeywordRow(UUID plannerId, String catalogKeywords, String contentKeywords) {
    }

    public record EntityFilterRow(UUID plannerId, String entityType, int entityId) {
    }

    public record KeywordFilterRow(UUID plannerId, String keyword) {
    }

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

    public List<UUID> visiblePlannersWithoutCatalogRow() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(p.id) AS planner_id
                FROM planner p
                JOIN planner_content c ON c.planner_id = p.id
                JOIN planner_publication pub ON pub.planner_id = p.id
                JOIN users u ON u.id = p.user_id
                LEFT JOIN planner_moderation m ON m.planner_id = p.id
                LEFT JOIN planner_catalog cat ON cat.planner_id = p.id
                WHERE pub.published = TRUE AND c.deleted_at IS NULL AND u.deleted_at IS NULL
                  AND m.taken_down_at IS NULL AND cat.planner_id IS NULL
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("planner_id")));
    }

    public List<UUID> catalogRowsWithoutVisiblePlanner() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id
                FROM planner_catalog cat
                LEFT JOIN planner_content c ON c.planner_id = cat.planner_id
                LEFT JOIN planner_publication pub ON pub.planner_id = cat.planner_id
                LEFT JOIN planner_moderation m ON m.planner_id = cat.planner_id
                LEFT JOIN planner p ON p.id = cat.planner_id
                LEFT JOIN users u ON u.id = p.user_id
                WHERE pub.planner_id IS NULL OR pub.published = FALSE
                   OR c.deleted_at IS NOT NULL OR m.taken_down_at IS NOT NULL
                   OR u.deleted_at IS NOT NULL
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("planner_id")));
    }

    /**
     * {@code utf8mb4_unicode_ci} is case-insensitive, accent-insensitive and PAD SPACE: a
     * copy left behind by a rename that only changed capitalization, or one carrying a trailing
     * space, is byte-stale and collation-equal. The {@code _0900_} form is the one that answers
     * the trailing space — {@code utf8mb4_bin} is itself PAD SPACE.
     */
    public List<CatalogScalarDriftRow> driftedCatalogScalars() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id, 'title' AS field,
                       c.title AS expected, cat.title AS actual
                FROM planner_catalog cat
                JOIN planner_content c ON c.planner_id = cat.planner_id
                WHERE c.deleted_at IS NULL
                  AND NOT (cat.title <=> c.title COLLATE utf8mb4_0900_bin)
                UNION ALL
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id, 'category' AS field,
                       c.category AS expected, cat.category AS actual
                FROM planner_catalog cat
                JOIN planner_content c ON c.planner_id = cat.planner_id
                WHERE c.deleted_at IS NULL
                  AND NOT (cat.category <=> c.category COLLATE utf8mb4_0900_bin)
                """,
                (rs, rowNum) -> new CatalogScalarDriftRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("field"), rs.getString("expected"), rs.getString("actual")));
    }

    public List<CatalogKeywordRow> catalogKeywordPairs() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id,
                       cat.selected_keywords AS catalog_keywords,
                       c.selected_keywords AS content_keywords
                FROM planner_catalog cat
                JOIN planner_content c ON c.planner_id = cat.planner_id
                WHERE c.deleted_at IS NULL
                """,
                (rs, rowNum) -> new CatalogKeywordRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("catalog_keywords"), rs.getString("content_keywords")));
    }

    public List<ContentDocumentRow> visibleContentDocuments() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(c.planner_id) AS planner_id, c.category, c.content
                FROM planner_content c
                JOIN planner_publication pub ON pub.planner_id = c.planner_id
                LEFT JOIN planner_moderation m ON m.planner_id = c.planner_id
                WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
                """,
                (rs, rowNum) -> new ContentDocumentRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("category"), rs.getString("content")));
    }

    public List<ContentKeywordRow> contentKeywordPairs() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(planner_id) AS planner_id,
                       JSON_EXTRACT(content, '$.selectedKeywords') AS content_keywords,
                       selected_keywords AS column_keywords
                FROM planner_content
                WHERE deleted_at IS NULL
                """,
                (rs, rowNum) -> new ContentKeywordRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("content_keywords"), rs.getString("column_keywords")));
    }

    public List<EntityFilterRow> entityFilterEntries() {
        return jdbc.query("""
                SELECT BIN_TO_UUID(planner_id) AS planner_id, entity_type, entity_id
                FROM planner_entity_filter
                """,
                (rs, rowNum) -> new EntityFilterRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("entity_type"), rs.getInt("entity_id")));
    }

    public List<KeywordFilterRow> keywordFilterEntries() {
        return jdbc.query(
                "SELECT BIN_TO_UUID(planner_id) AS planner_id, keyword FROM planner_keyword_filter",
                (rs, rowNum) -> new KeywordFilterRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getString("keyword")));
    }

    /**
     * The stamp is written with {@code CURRENT_TIMESTAMP(6)}; a cutoff bound as an
     * {@code Instant} would be rendered by the driver in the JVM's zone and land hours off on any
     * non-UTC host.
     */
    public List<UUID> stampedRecommendationsWithoutEffect() {
        return namedJdbc.query("""
                SELECT BIN_TO_UUID(s.planner_id) AS planner_id
                FROM planner_stats s
                LEFT JOIN domain_events e
                       ON e.aggregate_id = s.planner_id AND e.event_type = 'PLANNER_RECOMMENDED'
                LEFT JOIN notifications n
                       ON n.content_id = BIN_TO_UUID(s.planner_id)
                      AND n.notification_type = 'PLANNER_RECOMMENDED'
                WHERE s.recommended_notified_at IS NOT NULL
                  AND s.recommended_notified_at > DATE_SUB(NOW(6), INTERVAL :windowDays DAY)
                  AND e.id IS NULL
                  AND n.id IS NULL
                """,
                Map.of("windowDays", RECOMMENDED_AUDIT_WINDOW_DAYS),
                (rs, rowNum) -> UUID.fromString(rs.getString("planner_id")));
    }

    public List<RecommendedDriftRow> driftedRecommendedFlags(int threshold) {
        return namedJdbc.query(RecommendedSql.DRIFTED_ROWS, Map.of("threshold", threshold),
                (rs, rowNum) -> new RecommendedDriftRow(UUID.fromString(rs.getString("planner_id")),
                        rs.getBoolean("recommended"), rs.getBoolean("derived")));
    }
}
