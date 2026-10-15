package org.danteplanner.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;
import org.danteplanner.backend.planner.precondition.VoteUniquenessValidator;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PlannerModerationTargetAdapter;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerScopeDeclarationTest {

    private static final long OWNER_ID = 7L;
    private static final long READER_ID = 8L;
    private static final int MAX_PLANNERS = 100;
    private static final int SCHEMA_VERSION = 2;
    private static final int RECOMMENDED_THRESHOLD = 10;

    @Mock
    private PlannerRepository plannerRepository;
    @Mock
    private PlannerStatsRepository statsRepository;
    @Mock
    private PlannerCatalogRepository catalogRepository;
    @Mock
    private PlannerSubscriptionRepository subscriptionRepository;
    @Mock
    private PlannerVoteRepository voteRepository;
    @Mock
    private PlannerReportRepository reportRepository;
    @Mock
    private DomainEventRepository domainEventRepository;

    @TempDir
    private Path dataDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeUsers users = new FakeUsers();
    private User owner;

    @BeforeEach
    void setUp() {
        owner = users.add(TestDataFactory.unsavedUser(OWNER_ID));
        users.add(TestDataFactory.user().id(READER_ID).build());
        lenient().when(domainEventRepository.insert(any(DomainEvent.class))).thenAnswer(invocation -> {
            DomainEvent row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 1L);
            return row;
        });
        TransactionSynchronizationManager.initSynchronization();
        FreshnessDeclarations.begin();
    }

    @AfterEach
    void tearDown() {
        FreshnessDeclarations.clear();
        TransactionSynchronizationManager.clearSynchronization();
    }

    private PlannerAccessGuard accessGuard() {
        return new PlannerAccessGuard(users, plannerRepository);
    }

    private DomainEventRecorder recorder() {
        return new DomainEventRecorder(domainEventRepository, mapper, event -> { });
    }

    private PlannerCatalogService catalogService() {
        return new PlannerCatalogService(catalogRepository, plannerRepository, statsRepository, recorder(), mapper,
                new RecordingTombstoneStore(), RECOMMENDED_THRESHOLD);
    }

    private PlannerCommandService commandService(PlannerContentValidator contentValidator) {
        return new PlannerCommandService(plannerRepository, statsRepository, contentValidator,
                new ContentVersionValidator(TestDataFactory.gameDataRegistry()), catalogService(), accessGuard(),
                new RestrictionCheckService(users, new AccountStandingValidator()), new PlannerCategoryValidator(),
                new PlannerLimitValidator(), new PlannerOwnershipValidator(), new SyncVersionValidator(), mapper,
                MAX_PLANNERS, SCHEMA_VERSION);
    }

    private PlannerPublishingService publishingService(PlannerContentValidator contentValidator) {
        PlannerAccessGuard accessGuard = accessGuard();
        return new PlannerPublishingService(plannerRepository, statsRepository, commandService(contentValidator),
                contentValidator, catalogService(),
                new PlannerSubscriptionService(subscriptionRepository, accessGuard,
                        new RestrictionCheckService(users, new AccountStandingValidator())),
                accessGuard, new PlannerOwnershipValidator(), new PlannerPublishValidator(),
                new PlannerCategoryValidator(), recorder());
    }

    private PlannerContentValidator validatorRenaming(String idMigrations) throws IOException {
        try (var entries = Files.list(Path.of("../static/data"))) {
            for (Path entry : entries.toList()) {
                Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
            }
        }
        Files.writeString(dataDir.resolve(GameDataRegistry.ID_MIGRATIONS_FILE), idMigrations);
        GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(mapper), dataDir.toString());
        registry.init();
        return new PlannerContentValidator(mapper, 51200, 1024, registry, new SinnerIdValidator(),
                new FloorRules(registry));
    }

    private Planner planner(boolean published) {
        return TestDataFactory.planner(owner).published(published).build();
    }

    private Planner stored(Planner planner) {
        when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
        return planner;
    }

    @Test
    void upsertAggregate_WhenThePlannerIsNew_DeclaresItsEntityAndItsOwnersList() {
        UUID id = UUID.randomUUID();
        when(plannerRepository.insert(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commandService(TestDataFactory.contentValidator())
                .upsertAggregate(OWNER_ID, null, id, UpsertRequests.draft().build(), false);

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(id), new FreshnessScope.OwnerList(OWNER_ID));
    }

    @Test
    void upsertAggregate_WhenAPublishedPlannerChanges_DeclaresItsEntityItsOwnersListAndTheCatalog() {
        Planner planner = planner(true);

        edit(planner);

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.OwnerList(OWNER_ID),
                new FreshnessScope.PublicCatalog());
    }

    @Test
    void upsertAggregate_WhenADraftChanges_DeclaresItsEntityAndItsOwnersListOnly() {
        Planner planner = planner(false);

        edit(planner);

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.OwnerList(OWNER_ID));
    }

    private void edit(Planner planner) {
        when(plannerRepository.findAggregateForOwner(planner.getId(), OWNER_ID)).thenReturn(Optional.of(planner));

        commandService(TestDataFactory.contentValidator()).upsertAggregate(OWNER_ID, null, planner.getId(),
                UpsertRequests.draft().id(planner.getId().toString()).title("edited-" + UUID.randomUUID()).build(),
                true);
    }

    @Test
    void deletePlanner_WhenItSucceeds_DeclaresItsEntityItsOwnersListAndTheCatalog() {
        Planner planner = planner(true);
        when(plannerRepository.findAggregateForOwner(planner.getId(), OWNER_ID)).thenReturn(Optional.of(planner));

        commandService(TestDataFactory.contentValidator()).deletePlanner(OWNER_ID, planner.getId());

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.OwnerList(OWNER_ID),
                new FreshnessScope.PublicCatalog());
    }

    @Test
    void publish_WhenTheStoredContentMigrates_DeclaresItsEntityItsOwnersListAndTheCatalog() throws IOException {
        PlannerContentValidator renaming = validatorRenaming("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
        Planner planner = stored(TestDataFactory.planner(owner).published(false)
                .content(TestDataFactory.VALID_CONTENT.replace(
                        "\"observationGiftIds\":[]", "\"observationGiftIds\":[\"19247\"]"))
                .build());

        publishingService(renaming).publish(OWNER_ID, planner.getId());

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.OwnerList(OWNER_ID),
                new FreshnessScope.PublicCatalog());
    }

    @Test
    void publish_WhenTheContentNeedsNoMigration_DeclaresItsEntityAndTheCatalog() {
        Planner planner = stored(planner(false));

        publishingService(TestDataFactory.contentValidator()).publish(OWNER_ID, planner.getId());

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.PublicCatalog());
    }

    @Test
    void unpublish_WhenThePlannerWasPublished_DeclaresItsEntityAndTheCatalog() {
        Planner planner = stored(planner(true));

        publishingService(TestDataFactory.contentValidator()).unpublish(OWNER_ID, planner.getId());

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.PublicCatalog());
    }

    @Test
    void withdrawFromPublicView_WhenItTakesThePlannerDown_DeclaresItsEntityAndTheCatalog() {
        Planner planner = stored(planner(true));

        publishingService(TestDataFactory.contentValidator()).withdrawFromPublicView(planner.getId(),
                Planner::takeDown);

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.PublicCatalog());
    }

    @Test
    void changeRecommendedListing_WhenItHidesThePlanner_DeclaresTheCatalogOnly() {
        Planner planner = stored(planner(true));

        publishingService(TestDataFactory.contentValidator()).changeRecommendedListing(planner.getId(),
                listed -> listed.hideFromRecommended(READER_ID, "freshness"));

        assertThat(FreshnessDeclarations.declared()).containsExactly(new FreshnessScope.PublicCatalog());
    }

    @Test
    void toggleOwnerNotifications_WhenTheOwnerToggles_DeclaresItsEntityOnly() {
        Planner planner = stored(planner(true));

        publishingService(TestDataFactory.contentValidator()).toggleOwnerNotifications(OWNER_ID, planner.getId(),
                false);

        assertThat(FreshnessDeclarations.declared()).containsExactly(new FreshnessScope.Entity(planner.getId()));
    }

    @Test
    void castVote_WhenItIsTheFirstVote_DeclaresItsEntityAndTheCatalog() {
        Planner planner = planner(true);
        when(plannerRepository.findPublishedAggregate(planner.getId()))
                .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));
        PlannerAccessGuard accessGuard = accessGuard();
        PlannerEngagementService engagement = new PlannerEngagementService(voteRepository,
                new PlannerStatsService(statsRepository), catalogService(), recorder(), accessGuard,
                new PlannerReportService(reportRepository, new PlannerModerationTargetAdapter(null, accessGuard),
                        new ReportUniquenessValidator()),
                new VoteUniquenessValidator(), RECOMMENDED_THRESHOLD);

        engagement.castVote(READER_ID, planner.getId(), VoteType.UP);

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                new FreshnessScope.Entity(planner.getId()), new FreshnessScope.PublicCatalog());
    }

    @Test
    void accountDeactivated_WhenTheOwnerHasPublishedPlanners_DeclaresTheCatalogOnly() {
        when(plannerRepository.findPublishedIdsOwnedBy(OWNER_ID))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        catalogService().accountDeactivated(OWNER_ID);

        assertThat(FreshnessDeclarations.declared()).containsExactly(new FreshnessScope.PublicCatalog());
    }

    @Test
    void accountReactivated_WhenTheOwnerHasPublishedPlanners_DeclaresTheCatalogOnly() {
        when(plannerRepository.findPublishedIdsOwnedBy(OWNER_ID))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        catalogService().accountReactivated(OWNER_ID);

        assertThat(FreshnessDeclarations.declared()).containsExactly(new FreshnessScope.PublicCatalog());
    }
}
