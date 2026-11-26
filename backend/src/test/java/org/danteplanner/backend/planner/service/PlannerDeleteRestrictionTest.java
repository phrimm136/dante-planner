package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.support.OwnershipRows;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerDeleteRestrictionTest {

    @Mock
    private PlannerRepository plannerRepository;


    @Mock

    private PlannerContentRepository contentRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerPublicationRepository publicationRepository;

    private final FakeUsers users = new FakeUsers();

    private PlannerAccessGuard accessGuard;
    private PlannerCommandService commandService;
    private User owner;

    @BeforeEach
    void setUp() {
        accessGuard = new PlannerAccessGuard(users, plannerRepository);
        ObjectMapper objectMapper = new ObjectMapper();
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository, null,
                entityFilterRepository, keywordFilterRepository, null,
                objectMapper, 10);
        commandService = new PlannerCommandService(
                plannerRepository,
                contentRepository,
                null,
                publicationRepository,
                null,
                null,
                catalogService,
                accessGuard,
                new RestrictionCheckService(users, Clock.systemUTC()),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                objectMapper, Clock.systemUTC(),
                100,
                2);
        owner = users.add(TestDataFactory.unsavedUser(1L));
    }

    private StoredPlanner ownedPlanner(boolean published) {
        StoredPlanner planner = TestDataFactory.planner(owner).published(published).build();
        when(plannerRepository.findAggregateForOwner(planner.id(), owner.getId()))
                .thenReturn(Optional.of(planner));
        when(plannerRepository.findOwnershipById(planner.id()))
                .thenReturn(Optional.of(OwnershipRows.of(planner)));
        return planner;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void deleteAndCommit(StoredPlanner planner) {
        when(contentRepository.markDeleted(eq(planner.id()), any())).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        commandService.deletePlanner(owner.getId(), planner.id());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void assertWithdrawnFromTheCatalog(StoredPlanner planner) {
        verify(catalogRepository).deleteByPlannerId(planner.id());
    }

    private void ban() {
        TestDataFactory.ban(owner, Instant.now(), 2L);
    }

    private void timeOut() {
        TestDataFactory.timeOut(owner, Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void deletePlanner_WhenOwnerBanned_WithdrawsThePublishedPlanner() {
        ban();
        StoredPlanner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        verify(publicationRepository).unpublish(planner.id());
        verify(contentRepository).markDeleted(eq(planner.id()), any());
        assertWithdrawnFromTheCatalog(planner);
    }

    @Test
    void deletePlanner_WhenOwnerTimedOut_WithdrawsThePublishedPlanner() {
        timeOut();
        StoredPlanner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        verify(publicationRepository).unpublish(planner.id());
        verify(contentRepository).markDeleted(eq(planner.id()), any());
        assertWithdrawnFromTheCatalog(planner);
    }

    @Test
    void deletePlanner_WhenOwnerBanned_DeletesTheUnpublishedDraft() {
        ban();
        StoredPlanner planner = ownedPlanner(false);

        deleteAndCommit(planner);

        verify(contentRepository).markDeleted(eq(planner.id()), any());
    }

    @Test
    void deletePlanner_WhenOwnerUnrestricted_WithdrawsThePublishedPlanner() {
        StoredPlanner planner = ownedPlanner(true);

        deleteAndCommit(planner);

        verify(publicationRepository).unpublish(planner.id());
        verify(contentRepository).markDeleted(eq(planner.id()), any());
        assertWithdrawnFromTheCatalog(planner);
    }
}
