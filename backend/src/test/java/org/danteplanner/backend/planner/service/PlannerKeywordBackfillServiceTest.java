package org.danteplanner.backend.planner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import tools.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

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
        KeywordBackfillRow stale = new KeywordBackfillRow(plannerId, 1L, "[\"Burst\"]", null, true, null, true);

        backfill(new RecordingBackfillRepository(stale, stale));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf",
                "refreshContentKeywords [\"Burst\"]", "syncFilterRows", "refreshCatalogKeywords [\"Burst\"]");
    }

    @Test
    void backfillPage_WhenTheContentChangedBeforeTheLock_DerivesTheCorrectionFromTheLockedRow() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow paged = new KeywordBackfillRow(plannerId, 1L, "[\"Burst\"]", null, true, null, true);
        KeywordBackfillRow locked = new KeywordBackfillRow(plannerId, 1L, "[\"Combustion\"]", null, true, null, true);

        backfill(new RecordingBackfillRepository(paged, locked));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf",
                "refreshContentKeywords [\"Combustion\"]", "syncFilterRows",
                "refreshCatalogKeywords [\"Combustion\"]");
    }

    @Test
    void backfillPage_WhenTheVersionMovedBetweenThePageAndTheStatement_LeavesTheRowAlone() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow paged = new KeywordBackfillRow(plannerId, 1L, "[\"Burst\"]", null, true, null, true);
        KeywordBackfillRow locked = new KeywordBackfillRow(plannerId, 2L, "[\"Burst\"]", null, true, null, true);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(paged, locked));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf", "refreshContentKeywords [\"Burst\"]");
        assertThat(page.corrected()).isZero();
    }

    @Test
    void backfillPage_WhenTheLockedRowNoLongerNeedsACorrection_WritesNothing() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow paged = new KeywordBackfillRow(plannerId, 1L, "[\"Burst\"]", null, true, null, true);
        KeywordBackfillRow locked = new KeywordBackfillRow(
                plannerId, 1L, "[\"Combustion\"]", "[\"Combustion\"]", true, "[\"Combustion\"]", true);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(paged, locked));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf");
        assertThat(page.corrected()).isZero();
    }

    @Test
    void backfillPage_WhenTheRowIsCurrent_TakesNoLock() {
        UUID plannerId = UUID.randomUUID();
        KeywordBackfillRow current = new KeywordBackfillRow(
                plannerId, 1L, "[\"Burst\"]", "[\"Burst\"]", false, null, false);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(current, current));

        assertThat(calls).isEmpty();
        assertThat(page).isEqualTo(new PlannerKeywordBackfillService.BackfillPage(
                new PlannerKeywordBackfillService.BackfillCursor.After(plannerId), 1, 0));
    }

    @Test
    void backfillPage_WhenOnlyTheColumnOfAPrivatePlannerIsStale_RewritesTheColumnAlone() {
        KeywordBackfillRow stale = new KeywordBackfillRow(
                UUID.randomUUID(), 1L, "[\"Burst\"]", "unreadable", false, null, false);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(stale, stale));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf", "refreshContentKeywords [\"Burst\"]");
        assertThat(page.corrected()).isOne();
    }

    @Test
    void backfillPage_WhenOnlyTheCatalogCopyIsStale_RewritesTheCatalogAlone() {
        KeywordBackfillRow stale = new KeywordBackfillRow(
                UUID.randomUUID(), 1L, "[\"Burst\"]", "[\"Burst\"]", true, null, true);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(stale, stale));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf", "refreshCatalogKeywords [\"Burst\"]");
        assertThat(page.corrected()).isOne();
    }

    @Test
    void backfillPage_WhenTheContentSelectsNothing_ClearsBothCopies() {
        KeywordBackfillRow stale = new KeywordBackfillRow(
                UUID.randomUUID(), 1L, null, "[\"Burst\"]", true, "[\"Burst\"]", true);

        backfill(new RecordingBackfillRepository(stale, stale));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf", "clearContentKeywords", "syncFilterRows",
                "clearCatalogKeywords");
    }

    @Test
    void backfillPage_WhenTheRowIsGoneUnderTheLock_CountsNoCorrection() {
        KeywordBackfillRow stale = new KeywordBackfillRow(UUID.randomUUID(), 1L, "[\"Burst\"]", null, true, null, true);

        PlannerKeywordBackfillService.BackfillPage page = backfill(new RecordingBackfillRepository(stale, null));

        assertThat(calls).containsExactly("lockContent", "keywordRowOf");
        assertThat(page.scanned()).isOne();
        assertThat(page.corrected()).isZero();
    }

    private PlannerKeywordBackfillService.BackfillPage backfill(RecordingBackfillRepository rows) {
        UUID plannerId = rows.paged.plannerId();
        lenient().when(plannerRepository.lockContent(plannerId)).thenAnswer(invocation -> {
            calls.add("lockContent");
            return 1L;
        });
        lenient().when(entityFilterRepository.floorScopeOf(plannerId)).thenAnswer(invocation -> {
            calls.add("syncFilterRows");
            return Optional.empty();
        });
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository,
                statsRepository, entityFilterRepository, keywordFilterRepository, null, new ObjectMapper(),
                10);
        return new PlannerKeywordBackfillService(rows, plannerRepository, catalogService, new ObjectMapper())
                .backfillPage(new PlannerKeywordBackfillService.BackfillCursor.Start(), 10);
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
        public List<KeywordBackfillRow> firstKeywordRows(int limit) {
            return List.of(paged);
        }

        @Override
        public Optional<KeywordBackfillRow> keywordRowOf(UUID plannerId) {
            calls.add("keywordRowOf");
            return Optional.ofNullable(locked);
        }

        @Override
        public int refreshContentKeywords(UUID plannerId, long readVersion, String keywords) {
            calls.add("refreshContentKeywords " + keywords);
            return readVersion == locked.syncVersion() ? 1 : 0;
        }

        @Override
        public int clearContentKeywords(UUID plannerId, long readVersion) {
            calls.add("clearContentKeywords");
            return readVersion == locked.syncVersion() ? 1 : 0;
        }

        @Override
        public int clearCatalogKeywords(UUID plannerId) {
            calls.add("clearCatalogKeywords");
            return 1;
        }

        @Override
        public int refreshCatalogKeywords(UUID plannerId, String keywords) {
            calls.add("refreshCatalogKeywords " + keywords);
            return 1;
        }
    }
}
