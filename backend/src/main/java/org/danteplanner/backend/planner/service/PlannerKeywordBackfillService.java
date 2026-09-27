package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.KeywordBackfillRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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
    private final PlannerFilterService filterService;
    private final ObjectMapper objectMapper;

    /**
     * One page of the backfill: the last planner it read, how many it read, and how many it corrected.
     */
    public record BackfillPage(UUID lastPlannerId, int scanned, int corrected) {
    }

    @Transactional
    public BackfillPage backfillPage(UUID after, int limit) {
        List<KeywordBackfillRow> rows = repository.keywordRowsAfter(after, limit);
        int corrected = 0;
        for (KeywordBackfillRow row : rows) {
            String derived = derivedColumn(row.contentKeywords());
            boolean columnStale = !Objects.equals(derived, canonical(row.columnKeywords()));
            boolean catalogStale = row.catalogued() && !Objects.equals(derived, canonical(row.catalogKeywords()));
            if (columnStale) {
                repository.refreshContentKeywords(row.plannerId(), derived);
                if (row.published()) {
                    filterService.rebuildFilters(row.plannerId());
                }
            }
            if (catalogStale) {
                repository.refreshCatalogKeywords(row.plannerId(), derived);
            }
            if (columnStale || catalogStale) {
                corrected++;
            }
        }
        UUID last = rows.isEmpty() ? after : rows.get(rows.size() - 1).plannerId();
        return new BackfillPage(last, rows.size(), corrected);
    }

    private String derivedColumn(String selectionJson) {
        JsonNode selection;
        try {
            selection = selectionJson == null ? MissingNode.getInstance() : objectMapper.readTree(selectionJson);
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Keyword list could not be serialized: " + sorted, e);
        }
    }
}
