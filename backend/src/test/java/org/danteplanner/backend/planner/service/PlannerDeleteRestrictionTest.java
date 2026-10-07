package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerContentLifecycle;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.danteplanner.backend.support.Rejections.assertRejected;

@ExtendWith(MockitoExtension.class)
class PlannerDeleteRestrictionTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    private final FakeUsers users = new FakeUsers();
    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();

    private PlannerAccessGuard accessGuard;
    private PlannerCommandService commandService;
    private User owner;

    @BeforeEach
    void setUp() {
        accessGuard = new PlannerAccessGuard(users, plannerRepository);
        ObjectMapper objectMapper = new ObjectMapper();
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository, null,
                new DomainEventRecorder(domainEventRepository, objectMapper, null), objectMapper, tombstoneStore, 10);
        commandService = new PlannerCommandService(
                plannerRepository,
                null,
                null,
                null,
                catalogService,
                accessGuard,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                objectMapper,
                100,
                2);
        owner = users.add(TestDataFactory.unsavedUser(1L));
    }

    private Planner ownedPlanner(boolean published) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .userId(owner.getId())
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .build();
        planner.attach(
                PlannerContentLifecycle.asPersisted(StoredPlannerContent.holding(PlannerContent.builder()
                        .title("Planner")
                        .category("5F")
                        .status(PlannerStatus.DRAFT)
                        .contentSchemaVersion(2)
                        .gameContentVersion(6)
                        .syncVersion(1L)
                        .lastModifiedAt(Instant.now())
                        .build(), "{}")),
                PlannerPublication.builder().published(published).build(),
                PlannerModeration.builder().build());
        when(plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()))
                .thenReturn(Optional.of(planner));
        return planner;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void deleteAndCommit(Planner planner) {
        TransactionSynchronizationManager.initSynchronization();
        commandService.deletePlanner(owner.getId(), planner.getId());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void assertWithdrawnFromTheCatalog(Planner planner) {
        verify(catalogRepository).deleteById(planner.getId());
        assertTrue(tombstoneStore.isTombstoned(ByIdReadGuard.PUBLISHED_PLANNER_SCOPE, planner.getId()));
    }

    private void ban() {
        owner.setBannedAt(Instant.now());
        owner.setBannedBy(2L);
    }

    private void timeOut() {
        owner.setTimeoutUntil(Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void accessGuard_WhenOwnerBanned_RefusesTheRestrictionCheck() {
        ban();

        assertRejected(() -> accessGuard.checkNotRestricted(owner.getId())).withCode("USER_BANNED");
    }

    @Test
    void accessGuard_WhenOwnerTimedOut_RefusesTheRestrictionCheck() {
        timeOut();

        assertRejected(() -> accessGuard.checkNotRestricted(owner.getId())).withCode("USER_TIMED_OUT");
    }

    @Test
    void deletePlanner_WhenOwnerBanned_WithdrawsThePublishedPlanner() {
        ban();
        Planner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        assertFalse(planner.isPublished());
        assertNotNull(planner.getContent().getDeletedAt());
        assertWithdrawnFromTheCatalog(planner);
    }

    @Test
    void deletePlanner_WhenOwnerTimedOut_WithdrawsThePublishedPlanner() {
        timeOut();
        Planner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        assertFalse(planner.isPublished());
        assertNotNull(planner.getContent().getDeletedAt());
        assertWithdrawnFromTheCatalog(planner);
    }

    @Test
    void deletePlanner_WhenOwnerBanned_DeletesTheUnpublishedDraft() {
        ban();
        Planner planner = ownedPlanner(false);

        deleteAndCommit(planner);

        assertNotNull(planner.getContent().getDeletedAt());
    }

    @Test
    void deletePlanner_WhenOwnerUnrestricted_WithdrawsThePublishedPlanner() {
        Planner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        assertFalse(planner.isPublished());
        assertNotNull(planner.getContent().getDeletedAt());
        assertWithdrawnFromTheCatalog(planner);
    }
}
