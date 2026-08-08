package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.ContentDocumentRow;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.CounterDriftRow;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.EntityFilterRow;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.KeywordFilterRow;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.RecommendedDriftRow;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
@Slf4j
public class PlannerDriftReconciler {

    private static final String METRIC_NAME = "planner_reconciler_drift_total";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PlannerDriftAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int recommendedThreshold;

    public PlannerDriftReconciler(
            PlannerDriftAuditRepository auditRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.auditRepository = auditRepository;
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
     * <p>The read-only boundary spans the whole pass: every audit read routes to the replica, and
     * one transaction gives them a single snapshot to disagree against.</p>
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
        for (CounterDriftRow row : auditRepository.driftedUpvoteCounters()) {
            records.add(new DriftRecord(row.plannerId(), "upvotes",
                    String.valueOf(row.recounted()), String.valueOf(row.counter())));
        }
    }

    private void checkCommentCounts(List<DriftRecord> records) {
        for (CounterDriftRow row : auditRepository.driftedCommentCounters()) {
            records.add(new DriftRecord(row.plannerId(), "comment_count",
                    String.valueOf(row.recounted()), String.valueOf(row.counter())));
        }
    }

    private void checkCatalogMembership(List<DriftRecord> records) {
        for (UUID plannerId : auditRepository.visiblePlannersWithoutCatalogRow()) {
            records.add(new DriftRecord(plannerId, "catalog_membership", "row present", "row missing"));
        }
        for (UUID plannerId : auditRepository.catalogRowsWithoutVisiblePlanner()) {
            records.add(new DriftRecord(plannerId, "catalog_membership", "row absent", "row present"));
        }
    }

    private void checkFilters(List<DriftRecord> records) {
        // Expected index state: the same extraction the runtime maintenance runs,
        // over every visible planner's stored content and keywords
        Map<UUID, Set<String>> expectedEntities = new HashMap<>();
        Map<UUID, Set<String>> expectedKeywords = new HashMap<>();
        Set<UUID> unreadable = new HashSet<>();
        for (ContentDocumentRow row : auditRepository.visibleContentDocuments()) {
            UUID plannerId = row.plannerId();
            Set<String> entities = extractEntityKeys(row.content()).orElse(null);
            Set<String> keywords = parseKeywords(row.selectedKeywords()).orElse(null);

            if (entities == null || keywords == null) {
                log.warn("Planner {} skipped this reconciliation cycle: stored content or "
                        + "keywords could not be read", plannerId);
                unreadable.add(plannerId);
                continue;
            }
            expectedEntities.put(plannerId, entities);
            expectedKeywords.put(plannerId, keywords);
        }

        Map<UUID, Set<String>> actualEntities = new HashMap<>();
        for (EntityFilterRow row : auditRepository.entityFilterEntries()) {
            actualEntities.computeIfAbsent(row.plannerId(), k -> new HashSet<>())
                    .add(row.entityType() + ":" + row.entityId());
        }
        Map<UUID, Set<String>> actualKeywords = new HashMap<>();
        for (KeywordFilterRow row : auditRepository.keywordFilterEntries()) {
            actualKeywords.computeIfAbsent(row.plannerId(), k -> new HashSet<>())
                    .add(row.keyword());
        }

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
        for (RecommendedDriftRow row : auditRepository.driftedRecommendedFlags(recommendedThreshold)) {
            records.add(new DriftRecord(row.plannerId(), "recommended",
                    String.valueOf(row.derived()), String.valueOf(row.flag())));
        }
    }
}
