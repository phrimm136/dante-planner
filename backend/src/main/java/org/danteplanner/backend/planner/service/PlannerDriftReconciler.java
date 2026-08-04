package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.repository.RecommendedSql;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled drift reconciler over the planner projections and counters: detects
 * divergence between planner_stats and the authoritative child aggregates,
 * catalog membership vs visibility, the filter indexes vs a rebuild of the
 * stored content (same extraction path as runtime maintenance), and the derived
 * recommended flag. Emits one structured drift record (log event + metric) per
 * finding and repairs NOTHING — drift means a maintenance bug to fix, not a
 * table to quietly patch.
 *
 * <p>Moderation is joined outer throughout: a planner with no moderation row is nothing-hidden and
 * not-taken-down, the same reading the catalog write side and {@code RecommendedSql} take. An inner
 * join would drop exactly those planners from every audit, which is the state most in need of one.</p>
 */
@Service
@Slf4j
public class PlannerDriftReconciler {

    private static final String METRIC_NAME = "planner_reconciler_drift_total";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int recommendedThreshold;

    public PlannerDriftReconciler(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(this.jdbc);
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.recommendedThreshold = recommendedThreshold;
    }

    /**
     * One detected divergence for one planner.
     */
    public record DriftRecord(UUID plannerId, String kind, String expected, String actual) {
    }

    @Scheduled(cron = "${planner.reconciler.cron:0 0 4 * * *}")
    public void runScheduled() {
        reconcile();
    }

    /**
     * Run all drift checks and emit a record per finding.
     *
     * @return the drift records found in this pass
     */
    @Transactional(readOnly = true)
    public List<DriftRecord> reconcile() {
        List<DriftRecord> records = new ArrayList<>();
        checkUpvotes(records);
        checkCommentCounts(records);
        checkCatalogMembership(records);
        checkFilters(records);
        checkRecommended(records);
        records.forEach(this::emit);
        log.info("Planner drift reconciliation finished: {} drifted finding(s)", records.size());
        return records;
    }

    private void emit(DriftRecord record) {
        log.warn("Planner drift detected: planner={} kind={} expected={} actual={}",
                record.plannerId(), record.kind(), record.expected(), record.actual());
        meterRegistry.counter(METRIC_NAME, "kind", record.kind()).increment();
    }

    private void checkUpvotes(List<DriftRecord> records) {
        jdbc.query("""
                SELECT BIN_TO_UUID(s.planner_id) AS planner_id, s.upvotes, COUNT(v.planner_id) AS votes
                FROM planner_stats s
                LEFT JOIN planner_votes v ON v.planner_id = s.planner_id
                GROUP BY s.planner_id, s.upvotes
                HAVING s.upvotes <> votes
                """,
                rs -> {
                    records.add(new DriftRecord(UUID.fromString(rs.getString("planner_id")),
                            "upvotes", String.valueOf(rs.getLong("votes")),
                            String.valueOf(rs.getInt("upvotes"))));
                });
    }

    private void checkCommentCounts(List<DriftRecord> records) {
        jdbc.query("""
                SELECT BIN_TO_UUID(s.planner_id) AS planner_id, s.comment_count,
                       COUNT(c.planner_id) AS live_comments
                FROM planner_stats s
                LEFT JOIN planner_comments c ON c.planner_id = s.planner_id AND c.deleted_at IS NULL
                GROUP BY s.planner_id, s.comment_count
                HAVING s.comment_count <> live_comments
                """,
                rs -> {
                    records.add(new DriftRecord(UUID.fromString(rs.getString("planner_id")),
                            "comment_count", String.valueOf(rs.getLong("live_comments")),
                            String.valueOf(rs.getInt("comment_count"))));
                });
    }

    private void checkCatalogMembership(List<DriftRecord> records) {
        // Visible planners missing their catalog row
        jdbc.query("""
                SELECT BIN_TO_UUID(p.id) AS planner_id
                FROM planner p
                JOIN planner_content c ON c.planner_id = p.id
                JOIN planner_publication pub ON pub.planner_id = p.id
                LEFT JOIN planner_moderation m ON m.planner_id = p.id
                LEFT JOIN planner_catalog cat ON cat.planner_id = p.id
                WHERE pub.published = TRUE AND c.deleted_at IS NULL
                  AND m.taken_down_at IS NULL AND cat.planner_id IS NULL
                """,
                rs -> {
                    records.add(new DriftRecord(UUID.fromString(rs.getString("planner_id")),
                            "catalog_membership", "row present", "row missing"));
                });
        // Catalog rows for planners that are no longer visible
        jdbc.query("""
                SELECT BIN_TO_UUID(cat.planner_id) AS planner_id
                FROM planner_catalog cat
                LEFT JOIN planner_content c ON c.planner_id = cat.planner_id
                LEFT JOIN planner_publication pub ON pub.planner_id = cat.planner_id
                LEFT JOIN planner_moderation m ON m.planner_id = cat.planner_id
                WHERE pub.planner_id IS NULL OR pub.published = FALSE
                   OR c.deleted_at IS NOT NULL OR m.taken_down_at IS NOT NULL
                """,
                rs -> {
                    records.add(new DriftRecord(UUID.fromString(rs.getString("planner_id")),
                            "catalog_membership", "row absent", "row present"));
                });
    }

    private void checkFilters(List<DriftRecord> records) {
        // Expected index state: the same extraction the runtime maintenance runs,
        // over every visible planner's stored content and keywords
        Map<UUID, Set<String>> expectedEntities = new HashMap<>();
        Map<UUID, Set<String>> expectedKeywords = new HashMap<>();
        Set<UUID> unreadable = new HashSet<>();
        jdbc.query("""
                SELECT BIN_TO_UUID(c.planner_id) AS planner_id, c.content, c.selected_keywords
                FROM planner_content c
                JOIN planner_publication pub ON pub.planner_id = c.planner_id
                LEFT JOIN planner_moderation m ON m.planner_id = c.planner_id
                WHERE pub.published = TRUE AND c.deleted_at IS NULL AND m.taken_down_at IS NULL
                """,
                rs -> {
                    UUID plannerId = UUID.fromString(rs.getString("planner_id"));
                    Optional<Set<String>> entities = extractEntityKeys(rs.getString("content"));
                    Optional<Set<String>> keywords = parseKeywords(rs.getString("selected_keywords"));

                    if (entities.isEmpty() || keywords.isEmpty()) {
                        log.warn("Planner {} skipped this reconciliation cycle: stored content or "
                                + "keywords could not be read", plannerId);
                        unreadable.add(plannerId);
                        return;
                    }
                    expectedEntities.put(plannerId, entities.get());
                    expectedKeywords.put(plannerId, keywords.get());
                });

        Map<UUID, Set<String>> actualEntities = new HashMap<>();
        jdbc.query("""
                SELECT BIN_TO_UUID(planner_id) AS planner_id, entity_type, entity_id
                FROM planner_entity_filter
                """,
                rs -> {
                    actualEntities.computeIfAbsent(UUID.fromString(rs.getString("planner_id")),
                                    k -> new HashSet<>())
                            .add(rs.getString("entity_type") + ":" + rs.getInt("entity_id"));
                });
        Map<UUID, Set<String>> actualKeywords = new HashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(planner_id) AS planner_id, keyword FROM planner_keyword_filter",
                rs -> {
                    actualKeywords.computeIfAbsent(UUID.fromString(rs.getString("planner_id")),
                                    k -> new HashSet<>())
                            .add(rs.getString("keyword"));
                });

        compareIndex(records, "entity_filter", expectedEntities, actualEntities, unreadable);
        compareIndex(records, "keyword_filter", expectedKeywords, actualKeywords, unreadable);
    }

    /**
     * Compare one index against its rebuild.
     *
     * <p>A planner whose stored document could not be rebuilt is left out entirely: its expected
     * set is unknown, and treating unknown as empty reports every indexed row it has as drift.</p>
     */
    private void compareIndex(List<DriftRecord> records, String kind,
            Map<UUID, Set<String>> expected, Map<UUID, Set<String>> actual, Set<UUID> unreadable) {
        Set<UUID> plannerIds = new HashSet<>(expected.keySet());
        plannerIds.addAll(actual.keySet());
        plannerIds.removeAll(unreadable);
        for (UUID plannerId : plannerIds) {
            Set<String> want = expected.getOrDefault(plannerId, Set.of());
            Set<String> have = actual.getOrDefault(plannerId, Set.of());
            if (!want.equals(have)) {
                records.add(new DriftRecord(plannerId, kind, String.valueOf(want), String.valueOf(have)));
            }
        }
    }

    /**
     * @return the entity keys the stored document carries, or empty when it cannot be read
     */
    private Optional<Set<String>> extractEntityKeys(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Optional.of(Set.of());
        }
        try {
            Set<String> keys = new HashSet<>();
            for (PlannerContentEntityExtractor.EntityRef ref
                    : PlannerContentEntityExtractor.extract(objectMapper.readTree(contentJson))) {
                keys.add(ref.type().name() + ":" + ref.id());
            }
            return Optional.of(keys);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Unreadable planner content during reconciliation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return the keywords the stored column carries, or empty when it cannot be read
     */
    private Optional<Set<String>> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return Optional.of(Set.of());
        }
        try {
            return Optional.of(
                    PlannerKeywords.fromStorage(objectMapper.readValue(keywordsJson, STRING_LIST)).asSet());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Unreadable planner keywords during reconciliation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void checkRecommended(List<DriftRecord> records) {
        namedJdbc.query(RecommendedSql.DRIFTED_ROWS, Map.of("threshold", recommendedThreshold),
                rs -> {
                    records.add(new DriftRecord(UUID.fromString(rs.getString("planner_id")),
                            "recommended", String.valueOf(rs.getBoolean("derived")),
                            String.valueOf(rs.getBoolean("recommended"))));
                });
    }
}
