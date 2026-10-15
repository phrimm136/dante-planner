package org.danteplanner.backend.planner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordBackfillRepository.KeywordBackfillRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.support.RecordingTombstoneStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerKeywordBackfillServiceTest {

    private final List<String> calls = new ArrayList<>();

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Test
    void backfillPage_WhenARowIsCorrected_LocksItsContentRowAndRereadsItBeforeAnyWrite() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow stale = new KeywordBackfillRow(plannerId, "[\"Burst\"]", null, true, null, true);

        backfill(new RecordingBackfillRepository(stale, stale));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf",
                "refreshContentKeywords [\"Burst\"]", "syncFilterRows", "refreshCatalogKeywords [\"Burst\"]");
    }

    @Test
    void backfillPage_WhenTheContentChangedBeforeTheLock_DerivesTheCorrectionFromTheLockedRow() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow paged = new KeywordBackfillRow(plannerId, "[\"Burst\"]", null, true, null, true);
        KeywordBackfillRow locked = new KeywordBackfillRow(plannerId, "[\"Combustion\"]", null, true, null, true);

        backfill(new RecordingBackfillRepository(paged, locked));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf",
                "refreshContentKeywords [\"Combustion\"]", "syncFilterRows",
                "refreshCatalogKeywords [\"Combustion\"]");
    }

    @Test
    void backfillPage_WhenTheLockedRowNoLongerNeedsACorrection_WritesNothing() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow paged = new KeywordBackfillRow(plannerId, "[\"Burst\"]", null, true, null, true);
        KeywordBackfillRow locked = new KeywordBackfillRow(
                plannerId, "[\"Combustion\"]", "[\"Combustion\"]", true, "[\"Combustion\"]", true);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(paged, locked));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf");
        assertThat(page.corrected()).isZero();
    }

    private PlannerKeywordBackfillService.BackfillPage backfill(RecordingBackfillRepository rows) {
        UUID plannerId = rows.paged.plannerId();
        when(plannerRepository.lockContent(plannerId)).thenAnswer(invocation -> {
            calls.add("lockContent");
            return 1L;
        });
        lenient().when(entityFilterRepository.floorScopeOf(plannerId)).thenAnswer(invocation -> {
            calls.add("syncFilterRows");
            return Optional.empty();
        });
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository,
                statsRepository, entityFilterRepository, keywordFilterRepository, null, new ObjectMapper(),
                new RecordingTombstoneStore(), 10);
        return new PlannerKeywordBackfillService(rows, plannerRepository, catalogService, new ObjectMapper())
                .backfillPage(null, 10);
    }

    private final class RecordingBackfillRepository extends PlannerKeywordBackfillRepository {

        private final KeywordBackfillRow paged;
        private final KeywordBackfillRow locked;

        RecordingBackfillRepository(KeywordBackfillRow paged, KeywordBackfillRow locked) {
            super(new SimpleDriverDataSource());
            this.paged = paged;
            this.locked = locked;
        }

        @Override
        public List<KeywordBackfillRow> keywordRowsAfter(UUID after, int limit) {
            return List.of(paged);
        }

        @Override
        public Optional<KeywordBackfillRow> keywordRowOf(UUID plannerId) {
            calls.add("keywordRowOf");
            return Optional.of(locked);
        }

        @Override
        public int refreshContentKeywords(UUID plannerId, String keywords) {
            calls.add("refreshContentKeywords " + keywords);
            return 1;
        }

        @Override
        public int refreshCatalogKeywords(UUID plannerId, String keywords) {
            calls.add("refreshCatalogKeywords " + keywords);
            return 1;
        }
    }
}
