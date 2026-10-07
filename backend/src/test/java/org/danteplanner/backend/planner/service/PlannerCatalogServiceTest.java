package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.RecordingTombstoneStore.Write;
import org.danteplanner.backend.support.RecordingTombstoneStore.Writes;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerCatalogServiceTest {

    private static final int THRESHOLD = 5;
    private static final Long OWNER_ID = 7L;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();

    private PlannerCatalogService catalogService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        catalogService = new PlannerCatalogService(catalogRepository, plannerRepository, statsRepository,
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                new ObjectMapper(), tombstoneStore, THRESHOLD);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void onBecameVisible_WhenCalled_AddsTheRowAndRecordsTheEvent() {
        Planner planner = loadedPlanner();

        catalogService.onBecameVisible(planner);

        verify(catalogRepository).insert(any(PlannerCatalog.class));
        verifyRecorded(planner.getId());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void onBecameInvisible_WhenCommitted_RemovesTheRowRecordsTheEventAndMarksIt() {
        UUID plannerId = UUID.randomUUID();

        catalogService.onBecameInvisible(plannerId);

        verify(catalogRepository).deleteById(plannerId);
        verifyRecorded(plannerId);
        assertThat(tombstoneStore.calls()).isEmpty();
        commit();
        assertThat(tombstoneStore.calls()).containsExactly(new Write("published-planner", plannerId));
    }

    @Test
    void accountDeactivated_WhenCommitted_WithdrawsRecordsAnEventPerPlannerAndMarksThem() {
        List<UUID> published = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(plannerRepository.findPublishedIdsOwnedBy(OWNER_ID)).thenReturn(published);

        catalogService.accountDeactivated(OWNER_ID);

        verify(catalogRepository).withdrawAllOwnedBy(OWNER_ID);
        published.forEach(this::verifyRecorded);
        assertThat(tombstoneStore.calls()).isEmpty();
        commit();
        assertThat(tombstoneStore.calls()).containsExactly(new Writes("published-planner", published));
    }

    @Test
    void accountReactivated_WhenCalled_RestoresAndRecordsAnEventPerPublishedPlanner() {
        List<UUID> published = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(plannerRepository.findPublishedIdsOwnedBy(OWNER_ID)).thenReturn(published);

        catalogService.accountReactivated(OWNER_ID);

        verify(catalogRepository).restoreAllOwnedBy(OWNER_ID, THRESHOLD);
        published.forEach(this::verifyRecorded);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void refreshRecommended_WhenCalled_PassesTheThreshold() {
        UUID plannerId = UUID.randomUUID();

        catalogService.refreshRecommended(plannerId);

        verify(catalogRepository).refreshRecommended(plannerId, THRESHOLD);
    }

    @Test
    void onVisibleEditCommitted_WhenCompositionUnchanged_CopiesScalarsWithoutEventOrSync() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "title", "Renamed");
        PlannerCatalog row = PlannerCatalog.builder().plannerId(planner.getId()).build();
        when(catalogRepository.findById(planner.getId())).thenReturn(Optional.of(row));

        catalogService.onVisibleEditCommitted(planner);

        assertThat(row.getTitle()).isEqualTo("Renamed");
        assertThat(row.getCategory()).isEqualTo(planner.getCategory());
        assertThat(row.getSelectedKeywords()).isEqualTo(planner.getSelectedKeywords());
        verifyNothingRecorded();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void onVisibleEditCommitted_WhenContentChanged_RecordsTheEvent() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "content", "{\"selectedGiftIds\":[\"9099\"]}");

        assertChangeRecorded(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenKeywordsChanged_RecordsTheEvent() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "selectedKeywords", Set.of("Burst"));

        assertChangeRecorded(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenCategoryChanged_RecordsTheEvent() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "category", "10F");

        assertChangeRecorded(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenContentIsTheSameDocumentReformatted_RecordsNothing() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "content",
                " " + planner.getContentJson().replace(",", " , ") + " ");

        catalogService.onVisibleEditCommitted(planner);

        verifyNothingRecorded();
    }

    @Test
    void onVisibleEditCommitted_WhenContentIsUnparseable_RecordsTheEvent() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "content", "{not json");

        assertChangeRecorded(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenKeywordsGoFromAbsentToEmpty_RecordsNothing() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "loadedSelectedKeywords", null);
        ReflectionTestUtils.setField(planner.getContent(), "selectedKeywords", Set.of());

        catalogService.onVisibleEditCommitted(planner);

        verifyNothingRecorded();
    }

    @Test
    void onVisibleEditCommitted_WhenLoadedContentIsAbsent_RecordsTheEvent() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "loadedContent", null);

        assertChangeRecorded(planner);
    }

    private void assertChangeRecorded(Planner planner) {
        catalogService.onVisibleEditCommitted(planner);

        verifyRecorded(planner.getId());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void verifyRecorded(UUID plannerId) {
        verify(domainEventRepository).insert(argThat((DomainEvent row) ->
                row.getEventType() == DomainEventType.PLANNER_CATALOG_CHANGED
                        && row.getAggregateId().equals(plannerId)
                        && row.getPayload().equals("{}")));
    }

    private void verifyNothingRecorded() {
        verify(domainEventRepository, never()).insert(any());
    }

    private static Planner loadedPlanner() {
        User owner = TestDataFactory.unsavedUser(OWNER_ID);
        Planner planner = TestDataFactory.planner(owner)
                .id(UUID.randomUUID())
                .category("5F")
                .selectedKeywords(Set.of("Sinking"))
                .published(true)
                .build();
        ReflectionTestUtils.invokeMethod(planner.getContent(), "onLoad");
        return planner;
    }
}
