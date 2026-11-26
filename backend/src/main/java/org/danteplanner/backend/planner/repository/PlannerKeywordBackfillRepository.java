package org.danteplanner.backend.planner.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PlannerKeywordBackfillRepository {

    private final JdbcTemplate jdbc;

    public PlannerKeywordBackfillRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record KeywordBackfillRow(UUID plannerId, long syncVersion, KeywordColumn contentKeywords,
            KeywordColumn columnKeywords, CatalogCopy catalog, PublishState publication) {
    }

    public sealed interface KeywordColumn {

        record Empty() implements KeywordColumn {
        }

        record Json(String text) implements KeywordColumn {
        }

        private static KeywordColumn of(@Nullable String text) {
            return text == null ? new Empty() : new Json(text);
        }
    }

    public sealed interface CatalogCopy {

        record Uncatalogued() implements CatalogCopy {
        }

        record Catalogued(KeywordColumn keywords) implements CatalogCopy {
        }

        private static CatalogCopy of(boolean catalogued, @Nullable String keywords) {
            return catalogued ? new Catalogued(KeywordColumn.of(keywords)) : new Uncatalogued();
        }
    }

    public enum PublishState {
        PUBLISHED,
        UNPUBLISHED;

        private static PublishState of(boolean published) {
            return published ? PUBLISHED : UNPUBLISHED;
        }
    }

    private static final String KEYWORD_ROW = """
            SELECT BIN_TO_UUID(c.planner_id) AS planner_id,
                   c.sync_version AS sync_version,
                   JSON_EXTRACT(c.content, '$.selectedKeywords') AS content_keywords,
                   c.selected_keywords AS column_keywords,
                   cat.planner_id IS NOT NULL AS catalogued,
                   cat.selected_keywords AS catalog_keywords,
                   COALESCE(pub.published, FALSE) AS published
            FROM planner_content c
            LEFT JOIN planner_catalog cat ON cat.planner_id = c.planner_id
            LEFT JOIN planner_publication pub ON pub.planner_id = c.planner_id
            """;

    private static final String CURRENT_LIVE_ROW = " AND sync_version = ? AND deleted_at IS NULL";

    private static final String PAGE_ORDER = "ORDER BY c.planner_id LIMIT ?";

    private static final RowMapper<KeywordBackfillRow> KEYWORD_ROW_MAPPER = (rs, rowNum) -> new KeywordBackfillRow(
            UUID.fromString(rs.getString("planner_id")), rs.getLong("sync_version"),
            KeywordColumn.of(rs.getString("content_keywords")), KeywordColumn.of(rs.getString("column_keywords")),
            CatalogCopy.of(rs.getBoolean("catalogued"), rs.getString("catalog_keywords")),
            PublishState.of(rs.getBoolean("published")));

    public List<KeywordBackfillRow> firstKeywordRows(int limit) {
        return jdbc.query(KEYWORD_ROW + PAGE_ORDER, KEYWORD_ROW_MAPPER, limit);
    }

    public List<KeywordBackfillRow> keywordRowsAfter(UUID after, int limit) {
        return jdbc.query(KEYWORD_ROW + "WHERE c.planner_id > UUID_TO_BIN(?) " + PAGE_ORDER, KEYWORD_ROW_MAPPER,
                after.toString(), limit);
    }

    public Optional<KeywordBackfillRow> keywordRowOf(UUID plannerId) {
        return jdbc.query(KEYWORD_ROW + "WHERE c.planner_id = UUID_TO_BIN(?)", KEYWORD_ROW_MAPPER,
                plannerId.toString()).stream().findFirst();
    }

    public int refreshContentKeywords(UUID plannerId, long readVersion, String keywords) {
        return jdbc.update("UPDATE planner_content SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)"
                + CURRENT_LIVE_ROW, keywords, plannerId.toString(), readVersion);
    }

    public int refreshCatalogKeywords(UUID plannerId, String keywords) {
        return jdbc.update("UPDATE planner_catalog SET selected_keywords = ? WHERE planner_id = UUID_TO_BIN(?)",
                keywords, plannerId.toString());
    }

    public int clearContentKeywords(UUID plannerId, long readVersion) {
        return jdbc.update("UPDATE planner_content SET selected_keywords = NULL WHERE planner_id = UUID_TO_BIN(?)"
                + CURRENT_LIVE_ROW, plannerId.toString(), readVersion);
    }

    public int clearCatalogKeywords(UUID plannerId) {
        return jdbc.update("UPDATE planner_catalog SET selected_keywords = NULL WHERE planner_id = UUID_TO_BIN(?)",
                plannerId.toString());
    }
}
