package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.planner.entity.MDCategory;
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

    public record DriftRecord(UUID plannerId, String kind, String expected, String actual) {
    }

    private record ExpectedIndexes(Map<UUID, Set<String>> entities, Map<UUID, Set<String>> keywords,
            Set<UUID> unreadable) {
    }

    /**
     * {@code @SchedulerLock} over the shared auth Redis lock store ensures the
     * pass fires once across the fleet, not once per pod. A refused pod's call returns null rather
     * than an empty list.
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
                        auditContentKeywords(),
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

    private List<DriftRecord> auditCatalogKeywords() {
        return auditRepository.catalogKeywordPairs().stream()
                .map(row -> {
                    Set<String> want = keywordsAsServed(row.plannerId(), "content", row.contentKeywords());
                    Set<String> have = keywordsAsServed(row.plannerId(), "catalog", row.catalogKeywords());
                    return want.equals(have)
                            ? Optional.<DriftRecord>empty()
                            : Optional.of(new DriftRecord(row.plannerId(), "catalog_keywords",
                                    String.valueOf(want), String.valueOf(have)));
                })
                .flatMap(Optional::stream)
                .toList();
    }

    private List<DriftRecord> auditContentKeywords() {
        return auditRepository.contentKeywordPairs().stream()
                .map(row -> selectedInContent(row.plannerId(), row.contentKeywords())
                        .flatMap(want -> indexDrift("content_keywords", row.plannerId(), want,
                                keywordsAsServed(row.plannerId(), "column", row.columnKeywords()))))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Set<String>> selectedInContent(UUID plannerId, String selectionJson) {
        try {
            JsonNode selection = selectionJson == null ? MissingNode.getInstance() : objectMapper.readTree(selectionJson);
            return Optional.of(PlannerKeywords.fromSelection(selection).asSet());
        } catch (JsonProcessingException e) {
            log.warn("Unreadable content keywords for planner {} during reconciliation: {}",
                    plannerId, e.getMessage());
            return Optional.empty();
        }
    }

    private Set<String> keywordsAsServed(UUID plannerId, String side, String keywordsJson) {
        return parseKeywords(plannerId, side, keywordsJson).orElseGet(Set::of);
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

    private ExpectedIndexes rebuildExpectedIndexes() {
        Map<UUID, Set<String>> entitiesByPlanner = new HashMap<>();
        Map<UUID, Set<String>> keywordsByPlanner = new HashMap<>();
        Set<UUID> unreadable = new HashSet<>();
        for (ContentDocumentRow row : auditRepository.visibleContentDocuments()) {
            UUID plannerId = row.plannerId();
            Optional<JsonNode> document = readContent(plannerId, row.content());
            Set<String> entities = document
                    .flatMap(tree -> extractEntityKeys(plannerId, row.category(), tree))
                    .orElse(null);
            Set<String> keywords = document
                    .map(tree -> PlannerKeywords.fromContent(tree).asSet())
                    .orElse(null);

            if (entities == null || keywords == null) {
                log.warn("Planner {} skipped this reconciliation cycle: stored content could not be read",
                        plannerId);
                unreadable.add(plannerId);
                continue;
            }
            entitiesByPlanner.put(plannerId, entities);
            keywordsByPlanner.put(plannerId, keywords);
        }
        return new ExpectedIndexes(entitiesByPlanner, keywordsByPlanner, unreadable);
    }

    private Optional<JsonNode> readContent(UUID plannerId, String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Optional.of(MissingNode.getInstance());
        }
        try {
            return Optional.of(objectMapper.readTree(contentJson));
        } catch (JsonProcessingException e) {
            log.warn("Unreadable content for planner {} during reconciliation: {}",
                    plannerId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Set<String>> extractEntityKeys(UUID plannerId, String category, JsonNode content) {
        if (content.isMissingNode()) {
            return Optional.of(Set.of());
        }
        try {
            Set<String> keys = new HashSet<>();
            for (PlannerContentEntityExtractor.EntityRef ref
                    : PlannerContentEntityExtractor.extract(content, MDCategory.fromValue(category))) {
                keys.add(ref.type().name() + ":" + ref.id());
            }
            return Optional.of(keys);
        } catch (IllegalArgumentException e) {
            log.warn("Unreadable content for planner {} during reconciliation: {}",
                    plannerId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Set<String>> parseKeywords(UUID plannerId, String side, String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return Optional.of(Set.of());
        }
        try {
            return Optional.of(
                    PlannerKeywords.fromStorage(objectMapper.readValue(keywordsJson, STRING_LIST)).asSet());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Unreadable {} keywords for planner {} during reconciliation: {}",
                    side, plannerId, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<UUID, Set<String>> actualEntityIndex() {
        return auditRepository.entityFilterEntries().stream()
                .collect(Collectors.groupingBy(EntityFilterRow::plannerId,
                        Collectors.mapping(row -> row.entityType() + ":" + row.entityId(),
                                Collectors.toSet())));
    }

    private Map<UUID, Set<String>> actualKeywordIndex() {
        return auditRepository.keywordFilterEntries().stream()
                .collect(Collectors.groupingBy(KeywordFilterRow::plannerId,
                        Collectors.mapping(KeywordFilterRow::keyword, Collectors.toSet())));
    }

    private List<DriftRecord> compareIndex(String kind, Map<UUID, Set<String>> expected,
            Map<UUID, Set<String>> actual, Set<UUID> unreadable) {
        return comparablePlannerIds(expected, actual, unreadable).stream()
                .map(plannerId -> indexDrift(kind, plannerId,
                        expected.getOrDefault(plannerId, Set.of()),
                        actual.getOrDefault(plannerId, Set.of())))
                .flatMap(Optional::stream)
                .toList();
    }

    private Set<UUID> comparablePlannerIds(Map<UUID, Set<String>> expected,
            Map<UUID, Set<String>> actual, Set<UUID> unreadable) {
        Set<UUID> plannerIds = new HashSet<>(expected.keySet());
        plannerIds.addAll(actual.keySet());
        plannerIds.removeAll(unreadable);
        return plannerIds;
    }

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
