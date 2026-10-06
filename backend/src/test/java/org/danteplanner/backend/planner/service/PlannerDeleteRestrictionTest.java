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
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class PlannerDeleteRestrictionTest {

    private final PlannerRepository plannerRepository = mock(PlannerRepository.class);
    private final PlannerCatalogService catalogService = mock(PlannerCatalogService.class);
    private final FakeUsers users = new FakeUsers();

    private PlannerAccessGuard accessGuard;
    private PlannerCommandService commandService;
    private User owner;

    @BeforeEach
    void setUp() {
        accessGuard = new PlannerAccessGuard(users, plannerRepository);
        commandService = new PlannerCommandService(
                plannerRepository,
                mock(PlannerStatsRepository.class),
                null,
                null,
                catalogService,
                accessGuard,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                new ObjectMapper(),
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

        commandService.deletePlanner(owner.getId(), planner.getId());

        assertFalse(planner.isPublished());
        assertNotNull(planner.getContent().getDeletedAt());
        verify(catalogService).onBecameInvisible(planner.getId());
    }

    @Test
    void deletePlanner_WhenOwnerTimedOut_WithdrawsThePublishedPlanner() {
        timeOut();
        Planner planner = ownedPlanner(true);

        commandService.deletePlanner(owner.getId(), planner.getId());

        assertFalse(planner.isPublished());
        assertNotNull(planner.getContent().getDeletedAt());
        verify(catalogService).onBecameInvisible(planner.getId());
    }

    @Test
    void deletePlanner_WhenOwnerBanned_DeletesTheUnpublishedDraft() {
        ban();
        Planner planner = ownedPlanner(false);

        commandService.deletePlanner(owner.getId(), planner.getId());

        assertNotNull(planner.getContent().getDeletedAt());
    }

    @Test
    void deletePlanner_WhenOwnerUnrestricted_WithdrawsThePublishedPlanner() {
        Planner planner = ownedPlanner(true);

        commandService.deletePlanner(owner.getId(), planner.getId());

        assertFalse(planner.isPublished());
        assertNotNull(planner.getContent().getDeletedAt());
        verify(catalogService).onBecameInvisible(planner.getId());
    }
}
