package org.danteplanner.backend.planner.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.KeywordBackfillRow;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Rewrites each planner's keyword column and catalog copy to the keywords its content selects.
 */
@Service
@RequiredArgsConstructor
public class PlannerKeywordBackfillService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PlannerKeywordBackfillRepository repository;
    private final PlannerRepository plannerRepository;
    private final PlannerCatalogService plannerCatalogService;
    private final ObjectMapper objectMapper;

    /**
     * One page of the backfill: the last planner it read, how many it read, and how many it corrected.
     */
    public record BackfillPage(UUID lastPlannerId, int scanned, int corrected) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BackfillPage backfillPage(UUID after, int limit) {
        List<KeywordBackfillRow> rows = repository.keywordRowsAfter(after, limit);
        int corrected = 0;
        for (KeywordBackfillRow candidate : rows) {
            if (!correctionOf(candidate).needed()) {
                continue;
            }
            plannerRepository.lockContent(candidate.plannerId());
            Optional<KeywordBackfillRow> locked = repository.keywordRowOf(candidate.plannerId());
            if (locked.isEmpty()) {
                continue;
            }
            KeywordBackfillRow row = locked.get();
            Correction correction = correctionOf(row);
            if (correction.columnStale()) {
                repository.refreshContentKeywords(row.plannerId(), correction.derived());
                if (row.published()) {
                    plannerCatalogService.syncFilterRows(row.plannerId());
                }
            }
            if (correction.catalogStale()) {
                repository.refreshCatalogKeywords(row.plannerId(), correction.derived());
            }
            if (correction.needed()) {
                corrected++;
            }
        }
        UUID last = rows.isEmpty() ? after : rows.get(rows.size() - 1).plannerId();
        return new BackfillPage(last, rows.size(), corrected);
    }


    private record Correction(String derived, boolean columnStale, boolean catalogStale) {

        boolean needed() {
            return columnStale || catalogStale;
        }
    }

    private Correction correctionOf(KeywordBackfillRow row) {
        String derived = derivedColumn(row.contentKeywords());
        return new Correction(derived,
                !Objects.equals(derived, canonical(row.columnKeywords())),
                row.catalogued() && !Objects.equals(derived, canonical(row.catalogKeywords())));
    }

    private String derivedColumn(String selectionJson) {
        JsonNode selection;
        try {
            selection = selectionJson == null ? MissingNode.getInstance() : objectMapper.readTree(selectionJson);
        } catch (JacksonException e) {
            throw new IllegalStateException("MySQL returned an unreadable JSON selection: " + selectionJson, e);
        }
        return serialized(List.copyOf(PlannerKeywords.fromSelection(selection).asSet()));
    }

    private String canonical(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            List<String> keywords = objectMapper.readValue(stored, STRING_LIST);
            return keywords == null || keywords.contains(null) ? stored : serialized(keywords);
        } catch (JacksonException e) {
            return stored;
        }
    }

    private String serialized(List<String> keywords) {
        List<String> sorted = keywords.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sorted);
        } catch (JacksonException e) {
            throw new IllegalStateException("Keyword list could not be serialized: " + sorted, e);
        }
    }
}
