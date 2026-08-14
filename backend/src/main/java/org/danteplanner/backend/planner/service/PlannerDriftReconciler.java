package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.ContentDocumentRow;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.EntityFilterRow;
import org.danteplanner.backend.planner.repository.PlannerDriftAuditRepository.KeywordFilterRow;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scheduled drift reconciler over the planner projections and counters: detects
 * divergence between planner_stats and the authoritative child aggregates,
 * catalog membership vs visibility, the catalog's scalar and keyword copies vs
 * the content row they were taken from, the filter indexes vs a rebuild of the
 * stored content (same extraction path as runtime maintenance), the derived
 * recommended flag, and recommendation latches whose announcement nothing
 * carries. Emits one structured drift record (log event + metric) per
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

    /**
     * The filter index state a rebuild of the stored documents implies, beside the planners whose
     * document could not be read and whose expected state is therefore unknown.
     */
    private record ExpectedIndexes(Map<UUID, Set<String>> entities, Map<UUID, Set<String>> keywords,
            Set<UUID> unreadable) {
    }

    /**
     * Run every audit and emit a record per finding.
     *
     * <p>The read-only boundary spans the whole pass: every audit read routes to the replica, and
     * one transaction gives them a single snapshot to disagree against.</p>
     *
     * <p>Multi-pod safe: {@code @SchedulerLock} over the shared auth Redis lock store ensures the
     * pass fires once across the fleet, not once per pod. A refused pod's call returns null rather
     * than an empty list — the scheduler discards it, and nothing else calls this method.</p>
     *
     * @return the drift records found in this pass
     */
    @Scheduled(cron = "${planner.reconciler.cron:0 0 4 * * *}")
    @SchedulerLock(name = "reconcilePlannerDrift", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional(readOnly = true)
    public List<DriftRecord> reconcile() {
        List<DriftRecord> records = Stream.of(
                        auditUpvotes(),
                        auditCommentCounts(),
                        auditCatalogMembership(),
                        auditCatalogScalars(),
                        auditCatalogKeywords(),
                        auditFilters(),
                        auditRecommended(),
                        auditRecommendedNotification())
                .flatMap(List::stream)
                .toList();
        records.forEach(this::emit);
        log.info("Planner drift reconciliation finished: {} drifted finding(s)", records.size());
        return records;
    }

    private void emit(DriftRecord record) {
        log.warn("Planner drift detected: planner={} kind={} expected={} actual={}",
                record.plannerId(), record.kind(), record.expected(), record.actual());
        meterRegistry.counter(METRIC_NAME, "kind", record.kind()).increment();
    }

    private List<DriftRecord> auditUpvotes() {
        return auditRepository.driftedUpvoteCounters().stream()
                .map(row -> new DriftRecord(row.plannerId(), "upvotes",
                        String.valueOf(row.recounted()), String.valueOf(row.counter())))
                .toList();
    }

    private List<DriftRecord> auditCommentCounts() {
        return auditRepository.driftedCommentCounters().stream()
                .map(row -> new DriftRecord(row.plannerId(), "comment_count",
                        String.valueOf(row.recounted()), String.valueOf(row.counter())))
                .toList();
    }

    private List<DriftRecord> auditCatalogMembership() {
        return Stream.concat(
                        auditRepository.visiblePlannersWithoutCatalogRow().stream()
                                .map(plannerId -> new DriftRecord(plannerId, "catalog_membership",
                                        "row present", "row missing")),
                        auditRepository.catalogRowsWithoutVisiblePlanner().stream()
                                .map(plannerId -> new DriftRecord(plannerId, "catalog_membership",
                                        "row absent", "row present")))
                .toList();
    }

    private List<DriftRecord> auditCatalogScalars() {
        return auditRepository.driftedCatalogScalars().stream()
                .map(row -> new DriftRecord(row.plannerId(), "catalog_" + row.field(),
                        String.valueOf(row.expected()), String.valueOf(row.actual())))
                .toList();
    }

    /**
     * Compare the catalog's keyword copy against the content row's.
     *
     * <p>Both sides go through the same normalization the runtime reads them with, because the
     * stored array is order-bearing and its ordering carries no meaning; a row whose column could
     * not be read is left out, as its expected state is unknown rather than empty.</p>
     *
     * @return one record per planner whose two keyword sets disagree
     */
    private List<DriftRecord> auditCatalogKeywords() {
        return auditRepository.catalogKeywordPairs().stream()
                .map(row -> {
                    Optional<Set<String>> want = parseKeywords(row.contentKeywords());
                    Optional<Set<String>> have = parseKeywords(row.catalogKeywords());
                    if (want.isEmpty() || have.isEmpty() || want.get().equals(have.get())) {
                        return Optional.<DriftRecord>empty();
                    }
                    return Optional.of(new DriftRecord(row.plannerId(), "catalog_keywords",
                            String.valueOf(want.get()), String.valueOf(have.get())));
                })
                .flatMap(Optional::stream)
                .toList();
    }

    private List<DriftRecord> auditFilters() {
        ExpectedIndexes expected = rebuildExpectedIndexes();
        return Stream.of(
                        compareIndex("entity_filter", expected.entities(), actualEntityIndex(),
                                expected.unreadable()),
                        compareIndex("keyword_filter", expected.keywords(), actualKeywordIndex(),
                                expected.unreadable()))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Rebuild the index state every visible planner's stored content and keywords imply, by the same
     * extraction the runtime maintenance runs.
     *
     * @return the rebuilt sets per planner, plus the planners that could not be rebuilt
     */
    private ExpectedIndexes rebuildExpectedIndexes() {
        Map<UUID, Set<String>> entitiesByPlanner = new HashMap<>();
        Map<UUID, Set<String>> keywordsByPlanner = new HashMap<>();
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
            entitiesByPlanner.put(plannerId, entities);
            keywordsByPlanner.put(plannerId, keywords);
        }
        return new ExpectedIndexes(entitiesByPlanner, keywordsByPlanner, unreadable);
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

    /**
     * @return the indexed entity references each planner currently carries
     */
    private Map<UUID, Set<String>> actualEntityIndex() {
        return auditRepository.entityFilterEntries().stream()
                .collect(Collectors.groupingBy(EntityFilterRow::plannerId,
                        Collectors.mapping(row -> row.entityType() + ":" + row.entityId(),
                                Collectors.toSet())));
    }

    /**
     * @return the indexed keywords each planner currently carries
     */
    private Map<UUID, Set<String>> actualKeywordIndex() {
        return auditRepository.keywordFilterEntries().stream()
                .collect(Collectors.groupingBy(KeywordFilterRow::plannerId,
                        Collectors.mapping(KeywordFilterRow::keyword, Collectors.toSet())));
    }

    /**
     * Compare one index against its rebuild.
     *
     * @return one record per planner whose indexed rows disagree with the rebuild
     */
    private List<DriftRecord> compareIndex(String kind, Map<UUID, Set<String>> expected,
            Map<UUID, Set<String>> actual, Set<UUID> unreadable) {
        return comparablePlannerIds(expected, actual, unreadable).stream()
                .map(plannerId -> indexDrift(kind, plannerId,
                        expected.getOrDefault(plannerId, Set.of()),
                        actual.getOrDefault(plannerId, Set.of())))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * <p>A planner whose stored document could not be rebuilt is left out entirely: its expected
     * set is unknown, and treating unknown as empty reports every indexed row it has as drift.</p>
     *
     * @return the planners whose two sides can be held against each other
     */
    private Set<UUID> comparablePlannerIds(Map<UUID, Set<String>> expected,
            Map<UUID, Set<String>> actual, Set<UUID> unreadable) {
        Set<UUID> plannerIds = new HashSet<>(expected.keySet());
        plannerIds.addAll(actual.keySet());
        plannerIds.removeAll(unreadable);
        return plannerIds;
    }

    /**
     * @return the record for one planner's index, or empty when the two sides agree
     */
    private Optional<DriftRecord> indexDrift(String kind, UUID plannerId, Set<String> want,
            Set<String> have) {
        return want.equals(have)
                ? Optional.empty()
                : Optional.of(new DriftRecord(plannerId, kind, String.valueOf(want), String.valueOf(have)));
    }

    private List<DriftRecord> auditRecommended() {
        return auditRepository.driftedRecommendedFlags(recommendedThreshold).stream()
                .map(row -> new DriftRecord(row.plannerId(), "recommended",
                        String.valueOf(row.derived()), String.valueOf(row.recommended())))
                .toList();
    }

    private List<DriftRecord> auditRecommendedNotification() {
        return auditRepository.stampedRecommendationsWithoutEffect().stream()
                .map(plannerId -> new DriftRecord(plannerId, "recommended_notification",
                        "event or notification row", "neither"))
                .toList();
    }
}
