package org.danteplanner.backend.planner.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.CatalogCopy;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.KeywordBackfillRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.KeywordColumn;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.PublishState;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.failure.WriteOutcome;
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
                case RowState.Stale stale -> correctUnderLock(candidate);
            };
        }
        return new BackfillPage(BackfillCursor.following(cursor, rows), rows.size(), corrected);
    }

    private int correctUnderLock(KeywordBackfillRow candidate) {
        plannerRepository.lockContent(candidate.plannerId());
        return repository.keywordRowOf(candidate.plannerId())
                .map(row -> correct(row, candidate.syncVersion()))
                .orElse(0);
    }

    private int correct(KeywordBackfillRow row, long pageVersion) {
        return switch (stateOf(row)) {
            case RowState.Current current -> 0;
            case RowState.Stale(List<Correction> corrections) -> applied(row.plannerId(), pageVersion, corrections);
        };
    }

    private int applied(UUID plannerId, long pageVersion, List<Correction> corrections) {
        for (Correction correction : corrections) {
            switch (apply(plannerId, pageVersion, correction)) {
                case APPLIED -> { }
                case NOTHING -> {
                    return 0;
                }
            }
        }
        return 1;
    }

    private WriteOutcome apply(UUID plannerId, long pageVersion, Correction correction) {
        return switch (correction) {
            case Correction.Content(KeywordColumn.Json json) ->
                    WriteOutcome.of(repository.refreshContentKeywords(plannerId, pageVersion, json.text()));
            case Correction.Content(KeywordColumn.Empty empty) ->
                    WriteOutcome.of(repository.clearContentKeywords(plannerId, pageVersion));
            case Correction.FilterRows filterRows -> {
                plannerCatalogService.syncFilterRows(plannerId);
                yield WriteOutcome.APPLIED;
            }
            case Correction.Catalog(KeywordColumn.Json json) -> {
                repository.refreshCatalogKeywords(plannerId, json.text());
                yield WriteOutcome.APPLIED;
            }
            case Correction.Catalog(KeywordColumn.Empty empty) -> {
                repository.clearCatalogKeywords(plannerId);
                yield WriteOutcome.APPLIED;
            }
        };
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

        static RowState of(PublishState publication, KeywordColumn derived, KeywordColumn column,
                CatalogCopy catalog) {
            List<Correction> corrections = new ArrayList<>();
            if (!derived.equals(column)) {
                corrections.add(new Correction.Content(derived));
                switch (publication) {
                    case PUBLISHED -> corrections.add(new Correction.FilterRows());
                    case UNPUBLISHED -> { }
                }
            }
            switch (catalog) {
                case CatalogCopy.Uncatalogued uncatalogued -> { }
                case CatalogCopy.Catalogued(KeywordColumn stored) -> {
                    if (!derived.equals(stored)) {
                        corrections.add(new Correction.Catalog(derived));
                    }
                }
            }
            return corrections.isEmpty() ? new Current() : new Stale(corrections);
        }
    }

    private RowState stateOf(KeywordBackfillRow row) {
        return RowState.of(row.publication(), derivedColumn(row.contentKeywords()), canonical(row.columnKeywords()),
                canonical(row.catalog()));
    }

    private KeywordColumn derivedColumn(KeywordColumn contentSelection) {
        JsonNode selection = switch (contentSelection) {
            case KeywordColumn.Empty empty -> MissingNode.getInstance();
            case KeywordColumn.Json(String text) -> selectionOf(text);
        };
        return serialized(List.copyOf(PlannerKeywords.fromSelection(selection).asSet()));
    }

    private JsonNode selectionOf(String selectionJson) {
        try {
            return objectMapper.readTree(selectionJson);
        } catch (JacksonException e) {
            throw new IllegalStateException("MySQL returned an unreadable JSON selection: " + selectionJson, e);
        }
    }

    private CatalogCopy canonical(CatalogCopy catalog) {
        return switch (catalog) {
            case CatalogCopy.Uncatalogued uncatalogued -> uncatalogued;
            case CatalogCopy.Catalogued(KeywordColumn stored) -> new CatalogCopy.Catalogued(canonical(stored));
        };
    }

    private KeywordColumn canonical(KeywordColumn column) {
        return switch (column) {
            case KeywordColumn.Empty empty -> empty;
            case KeywordColumn.Json(String stored) -> reserialized(stored);
        };
    }

    private KeywordColumn reserialized(String stored) {
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
