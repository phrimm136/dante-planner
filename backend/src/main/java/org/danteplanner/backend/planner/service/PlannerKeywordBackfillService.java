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

import java.util.ArrayList;
import java.util.List;
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
    public record BackfillPage(BackfillCursor next, int scanned, int corrected) {
    }

    /** Where the next backfill page begins. */
    public sealed interface BackfillCursor {

        /** Before the first planner. */
        record Start() implements BackfillCursor {
        }

        /** After the last planner a page read. */
        record After(UUID plannerId) implements BackfillCursor {
        }

        private static BackfillCursor following(BackfillCursor cursor, List<KeywordBackfillRow> page) {
            return page.isEmpty() ? cursor : new After(page.getLast().plannerId());
        }
    }

    private sealed interface KeywordColumn {

        record Empty() implements KeywordColumn {
        }

        record Json(String text) implements KeywordColumn {
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BackfillPage backfillPage(BackfillCursor cursor, int limit) {
        List<KeywordBackfillRow> rows = switch (cursor) {
            case BackfillCursor.Start start -> repository.firstKeywordRows(limit);
            case BackfillCursor.After after -> repository.keywordRowsAfter(after.plannerId(), limit);
        };
        int corrected = 0;
        for (KeywordBackfillRow candidate : rows) {
            corrected += switch (stateOf(candidate)) {
                case RowState.Current current -> 0;
                case RowState.Stale stale -> correctUnderLock(candidate.plannerId());
            };
        }
        return new BackfillPage(BackfillCursor.following(cursor, rows), rows.size(), corrected);
    }

    private int correctUnderLock(UUID plannerId) {
        plannerRepository.lockContent(plannerId);
        return repository.keywordRowOf(plannerId).map(this::correct).orElse(0);
    }

    private int correct(KeywordBackfillRow row) {
        return switch (stateOf(row)) {
            case RowState.Current current -> 0;
            case RowState.Stale(List<Correction> corrections) -> {
                corrections.forEach(correction -> apply(row.plannerId(), correction));
                yield 1;
            }
        };
    }

    private void apply(UUID plannerId, Correction correction) {
        switch (correction) {
            case Correction.Content(KeywordColumn.Json json) ->
                    repository.refreshContentKeywords(plannerId, json.text());
            case Correction.Content(KeywordColumn.Empty empty) -> repository.clearContentKeywords(plannerId);
            case Correction.FilterRows filterRows -> plannerCatalogService.syncFilterRows(plannerId);
            case Correction.Catalog(KeywordColumn.Json json) ->
                    repository.refreshCatalogKeywords(plannerId, json.text());
            case Correction.Catalog(KeywordColumn.Empty empty) -> repository.clearCatalogKeywords(plannerId);
        }
    }

    private sealed interface Correction {

        record Content(KeywordColumn derived) implements Correction {
        }

        record FilterRows() implements Correction {
        }

        record Catalog(KeywordColumn derived) implements Correction {
        }
    }

    private sealed interface RowState {

        record Current() implements RowState {
        }

        record Stale(List<Correction> corrections) implements RowState {
        }

        static RowState of(KeywordBackfillRow row, KeywordColumn derived, KeywordColumn column,
                KeywordColumn catalog) {
            List<Correction> corrections = new ArrayList<>();
            if (!derived.equals(column)) {
                corrections.add(new Correction.Content(derived));
                if (row.published()) {
                    corrections.add(new Correction.FilterRows());
                }
            }
            if (row.catalogued() && !derived.equals(catalog)) {
                corrections.add(new Correction.Catalog(derived));
            }
            return corrections.isEmpty() ? new Current() : new Stale(corrections);
        }
    }

    private RowState stateOf(KeywordBackfillRow row) {
        return RowState.of(row, derivedColumn(row.contentKeywords()), canonical(row.columnKeywords()),
                canonical(row.catalogKeywords()));
    }

    private KeywordColumn derivedColumn(String selectionJson) {
        JsonNode selection;
        try {
            selection = selectionJson == null ? MissingNode.getInstance() : objectMapper.readTree(selectionJson);
        } catch (JacksonException e) {
            throw new IllegalStateException("MySQL returned an unreadable JSON selection: " + selectionJson, e);
        }
        return serialized(List.copyOf(PlannerKeywords.fromSelection(selection).asSet()));
    }

    private KeywordColumn canonical(String stored) {
        if (stored == null) {
            return new KeywordColumn.Empty();
        }
        try {
            List<String> keywords = objectMapper.readValue(stored, STRING_LIST);
            return keywords == null || keywords.contains(null)
                    ? new KeywordColumn.Json(stored)
                    : serialized(keywords);
        } catch (JacksonException e) {
            return new KeywordColumn.Json(stored);
        }
    }

    private KeywordColumn serialized(List<String> keywords) {
        List<String> sorted = keywords.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return new KeywordColumn.Empty();
        }
        try {
            return new KeywordColumn.Json(objectMapper.writeValueAsString(sorted));
        } catch (JacksonException e) {
            throw new IllegalStateException("Keyword list could not be serialized: " + sorted, e);
        }
    }
}
