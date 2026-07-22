package db.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.service.PlannerContentEntityExtractor;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

/**
 * Planner god-table decomposition, step 2b of 4: rebuild the entity and keyword
 * inverted indexes for every visible planner. Runs the SAME extraction code as
 * the runtime filter maintenance ({@code PlannerContentEntityExtractor}), so the
 * backfilled index is guaranteed to match what a runtime rebuild would produce.
 * Scope is visible planners only, matching the clear-on-unpublish semantics.
 *
 * Rerunnable: INSERT IGNORE on the natural PKs.
 */
public class V051__Backfill_planner_filters extends BaseJavaMigration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VISIBLE_PLANNERS =
            "SELECT c.planner_id, c.content, c.selected_keywords "
                    + "FROM planner_content c "
                    + "JOIN planner_publication p ON p.planner_id = c.planner_id "
                    + "JOIN planner_moderation m ON m.planner_id = c.planner_id "
                    + "WHERE p.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL";

    private static final String INSERT_ENTITY =
            "INSERT IGNORE INTO planner_entity_filter (entity_type, entity_id, planner_id) VALUES (?, ?, ?)";

    private static final String INSERT_KEYWORD =
            "INSERT IGNORE INTO planner_keyword_filter (keyword, planner_id) VALUES (?, ?)";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement select = connection.createStatement();
             PreparedStatement insertEntity = connection.prepareStatement(INSERT_ENTITY);
             PreparedStatement insertKeyword = connection.prepareStatement(INSERT_KEYWORD);
             ResultSet rows = select.executeQuery(VISIBLE_PLANNERS)) {

            while (rows.next()) {
                byte[] plannerId = rows.getBytes("planner_id");

                String content = rows.getString("content");
                if (content != null && !content.isBlank()) {
                    JsonNode root = parseOrNull(content);
                    if (root != null) {
                        Set<PlannerContentEntityExtractor.EntityRef> refs =
                                PlannerContentEntityExtractor.extract(root);
                        for (PlannerContentEntityExtractor.EntityRef ref : refs) {
                            insertEntity.setString(1, ref.type().name());
                            insertEntity.setInt(2, ref.id());
                            insertEntity.setBytes(3, plannerId);
                            insertEntity.addBatch();
                        }
                    }
                }

                // Same normalization as the runtime write path: renames applied,
                // unknown members dropped, so the index answers the current ids
                for (String keyword : normalizedKeywords(rows.getString("selected_keywords"))) {
                    insertKeyword.setString(1, keyword);
                    insertKeyword.setBytes(2, plannerId);
                    insertKeyword.addBatch();
                }
            }

            insertEntity.executeBatch();
            insertKeyword.executeBatch();
        }
    }

    private static JsonNode parseOrNull(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            // Mirror the runtime tolerance: an unindexable document is skipped,
            // never allowed to abort the whole migration
            return null;
        }
    }

    private static Set<String> normalizedKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return Set.of();
        }
        try {
            List<String> stored = MAPPER.readValue(keywordsJson, new TypeReference<List<String>>() {
            });
            return PlannerKeywords.fromStorage(stored).asSet();
        } catch (Exception e) {
            return Set.of();
        }
    }
}
