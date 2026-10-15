package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.support.OwnershipRows;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerDeleteRestrictionTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

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
                entityFilterRepository, keywordFilterRepository, null,
                objectMapper, tombstoneStore, 10);
        commandService = new PlannerCommandService(
                plannerRepository,
                null,
                null,
                null,
                catalogService,
                accessGuard,
                new RestrictionCheckService(users, new AccountStandingValidator()),
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
        Planner planner = TestDataFactory.planner(owner).published(published).build();
        when(plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()))
                .thenReturn(Optional.of(planner));
        when(plannerRepository.findOwnershipById(planner.getId()))
                .thenReturn(Optional.of(OwnershipRows.of(planner)));
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
        verify(catalogRepository).deleteByPlannerId(planner.getId());
        assertThat(tombstoneStore.isTombstoned(ByIdReadGuard.PUBLISHED_PLANNER_SCOPE, planner.getId())).isTrue();
    }

    private void ban() {
        owner.setBannedAt(Instant.now());
        owner.setBannedBy(2L);
    }

    private void timeOut() {
        owner.setTimeoutUntil(Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void deletePlanner_WhenOwnerBanned_WithdrawsThePublishedPlanner() {
        ban();
        Planner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getContent().getDeletedAt()).isNotNull();
        assertWithdrawnFromTheCatalog(planner);
    }

    @Test
    void deletePlanner_WhenOwnerTimedOut_WithdrawsThePublishedPlanner() {
        timeOut();
        Planner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getContent().getDeletedAt()).isNotNull();
        assertWithdrawnFromTheCatalog(planner);
    }

    @Test
    void deletePlanner_WhenOwnerBanned_DeletesTheUnpublishedDraft() {
        ban();
        Planner planner = ownedPlanner(false);

        deleteAndCommit(planner);

        assertThat(planner.getContent().getDeletedAt()).isNotNull();
    }

    @Test
    void deletePlanner_WhenOwnerUnrestricted_WithdrawsThePublishedPlanner() {
        Planner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getContent().getDeletedAt()).isNotNull();
        assertWithdrawnFromTheCatalog(planner);
    }
}
