package org.danteplanner.backend.planner.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PlannerKeywordBackfillRepository {

    private final JdbcTemplate jdbc;

    public PlannerKeywordBackfillRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record KeywordBackfillRow(UUID plannerId, String contentKeywords, String columnKeywords,
            boolean catalogued, String catalogKeywords, boolean published) {
    }

    private static final String KEYWORD_ROW = """
            SELECT BIN_TO_UUID(c.planner_id) AS planner_id,
                   JSON_EXTRACT(c.content, '$.selectedKeywords') AS content_keywords,
                   c.selected_keywords AS column_keywords,
                   cat.planner_id IS NOT NULL AS catalogued,
                   cat.selected_keywords AS catalog_keywords,
                   COALESCE(pub.published, FALSE) AS published
            FROM planner_content c
            LEFT JOIN planner_catalog cat ON cat.planner_id = c.planner_id
            LEFT JOIN planner_publication pub ON pub.planner_id = c.planner_id
            """;

    private static final RowMapper<KeywordBackfillRow> KEYWORD_ROW_MAPPER = (rs, rowNum) -> new KeywordBackfillRow(
            UUID.fromString(rs.getString("planner_id")),
            rs.getString("content_keywords"), rs.getString("column_keywords"),
            rs.getBoolean("catalogued"), rs.getString("catalog_keywords"), rs.getBoolean("published"));

    public List<KeywordBackfillRow> keywordRowsAfter(UUID after, int limit) {
        String afterId = after == null ? null : after.toString();
        return jdbc.query(KEYWORD_ROW + """
                WHERE ? IS NULL OR c.planner_id > UUID_TO_BIN(?)
                ORDER BY c.planner_id
                LIMIT ?
                """, KEYWORD_ROW_MAPPER, afterId, afterId, limit);
    }

    public Optional<KeywordBackfillRow> keywordRowOf(UUID plannerId) {
        return jdbc.query(KEYWORD_ROW + "WHERE c.planner_id = UUID_TO_BIN(?)", KEYWORD_ROW_MAPPER,
                plannerId.toString()).stream().findFirst();
    }

    public int refreshContentKeywords(UUID plannerId, String keywords) {
        return jdbc.update("UPDATE planner_content SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                keywords, plannerId.toString());
    }

    public int refreshCatalogKeywords(UUID plannerId, String keywords) {
        return jdbc.update("UPDATE planner_catalog SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                keywords, plannerId.toString());
    }
}
