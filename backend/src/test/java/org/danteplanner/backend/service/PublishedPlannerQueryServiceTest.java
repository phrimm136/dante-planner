package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.planner.precondition.CatalogReadValidator;

import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.PlannerCoreInfo;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;

/**
 * Unit tests for PublishedPlannerQueryService (public catalog read model:
 * published/recommended listings, search, and single-planner detail).
 */
@ExtendWith(MockitoExtension.class)
class PublishedPlannerQueryServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerViewRepository plannerViewRepository;

    @Mock
    private PlannerSubscriptionRepository subscriptionRepository;

    @Mock
    private PlannerReportRepository reportRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    private PublishedPlannerQueryService publishedQueryService;

    private PlannerViewRecorder plannerViewRecorder;

    private User testUser;

    @BeforeEach
    void setUp() {
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(null, plannerRepository);
        plannerViewRecorder = new PlannerViewRecorder(plannerViewRepository, plannerStatsRepository);

        publishedQueryService = new PublishedPlannerQueryService(
                plannerRepository,
                catalogRepository,
                plannerVoteRepository,
                new PlannerSubscriptionService(subscriptionRepository, accessGuard),
                new PlannerReportService(reportRepository, null, null, null),
                plannerViewRecorder,
                null,
                plannerStatsRepository,
                accessGuard,
                new CatalogReadValidator()
        );

        testUser = TestDataFactory.unsavedUser(1L);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @SuppressWarnings("unchecked")
    private List<PlannerViewId> bufferedViews() {
        TransactionSynchronizationManager.initSynchronization();
        plannerViewRecorder.flush();
        ArgumentCaptor<Collection<PlannerViewId>> drained = ArgumentCaptor.forClass(Collection.class);
        verify(plannerViewRepository, atMost(1)).insertIgnoreReturningNewCounts(drained.capture(), any());
        return drained.getAllValues().stream().flatMap(Collection::stream).toList();
    }

    private PlannerCatalog catalogRow(String title, boolean recommended) {
        return PlannerCatalog.builder()
                .plannerId(UUID.randomUUID())
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .category("5F")
                .title(title)
                .firstPublishedAt(Instant.now())
                .recommended(recommended)
                .build();
    }

    /**
     * Every listing composes one Specification over the catalog projection; the mocked executor
     * answers whatever page the case seeds, so these assert the composition and the mapping, not
     * the predicate\u2019s SQL (which belongs to the containerized tier).
     */
    private void stubCatalogPage(Page<PlannerCatalog> page) {
        when(catalogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
    }

    private Page<PlannerCatalog> pageOf(Pageable pageable, PlannerCatalog... rows) {
        return new PageImpl<>(List.of(rows), pageable, rows.length);
    }

    @Nested
    class PublishedListingTests {

        @Test
        void publishedListing_WhenRowsExist_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(pageOf(pageable, catalogRow("Planner 1", false), catalogRow("Planner 2", false)));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().size()).isEqualTo(2);
        }

        @Test
        void publishedListing_WhenCategory_ComposesCategoryPredicate() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog inCategory = catalogRow("F5 Planner", false);
            stubCatalogPage(pageOf(pageable, inCategory));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, "5F", null, null, null), pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(1);
            PublicPlannerResponse card = result.getContent().get(0);
            assertThat(card.id()).isEqualTo(inCategory.getPlannerId());
            assertThat(card.title()).isEqualTo("F5 Planner");
            assertThat(card.category()).isEqualTo("5F");
        }

        @Test
        void publishedListing_WhenNoRows_ReturnsEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(new PageImpl<>(List.of(), pageable, 0));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        void publishedListing_WhenRowLacksStatsAndCore_DefaultsCountersToZero() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog row = catalogRow("Counterless", false);
            stubCatalogPage(pageOf(pageable, row));
            when(plannerRepository.findCoreInfoByIds(List.of(row.getPlannerId()))).thenReturn(List.of());
            when(plannerStatsRepository.findAllById(List.of(row.getPlannerId()))).thenReturn(List.of());

            PublicPlannerResponse card = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null)
                    .getContent().get(0);

            assertThat(card.upvotes()).isEqualTo(0);
            assertThat(card.viewCount()).isEqualTo(0);
            assertThat(card.commentCount()).isEqualTo(0L);
            assertThat(card.authorUsernameEpithet()).isNull();
            assertThat(card.authorUsernameSuffix()).isNull();
            assertThat(card.createdAt()).isNull();
        }
    }

    @Nested
    class RecommendedListingTests {

        @Test
        void recommendedListing_WhenRowsExist_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(pageOf(pageable, catalogRow("High Votes", true), catalogRow("Medium Votes", true)));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, null, null, null, null), pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void recommendedListing_WhenCategory_ComposesCategoryPredicate() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog inCategory = catalogRow("F10 Planner", true);
            stubCatalogPage(pageOf(pageable, inCategory));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, "10F", null, null, null), pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(1);
            PublicPlannerResponse card = result.getContent().get(0);
            assertThat(card.id()).isEqualTo(inCategory.getPlannerId());
            assertThat(card.title()).isEqualTo("F10 Planner");
        }

        @Test
        void recommendedListing_WhenNoRows_ReturnsEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(new PageImpl<>(List.of(), pageable, 0));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, null, null, null, null), pageable, null);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    class IncrementViewCountTests {

        @Test
        void incrementViewCount_WhenSuccess_IncrementsCount() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.existsActiveById(plannerId)).thenReturn(true);

            // Act
            publishedQueryService.incrementViewCount(plannerId);

            // Assert
            // The increment returns nothing and the counter lives in planner_stats; reading back
            // view_count instead of the call needs a committing (containerized) test.
            verify(plannerStatsRepository).incrementViewCountBy(plannerId, 1);
        }

        @Test
        void incrementViewCount_WhenPlannerMissingOrDeleted_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.existsActiveById(nonExistentId)).thenReturn(false);

            // Act & Assert
            assertRejected(() -> publishedQueryService.incrementViewCount(nonExistentId)).withCode("PLANNER_NOT_FOUND");
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
        }
    }

    @Nested
    class GetPublishedPlannerTests {

        private static final String IP_ADDRESS = "192.168.1.100";
        private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        private Planner createPublishedPlannerWithId(UUID id) {
            return TestDataFactory.planner(testUser).id(id).published(true).build();
        }

        private void mockNewView(UUID plannerId) {
            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(createPublishedPlannerWithId(plannerId), TestDataFactory.profileOf(testUser))));
            when(plannerStatsRepository.findById(plannerId))
                    .thenReturn(Optional.of(PlannerStats.builder()
                            .plannerId(plannerId)
                            .viewCount(10)
                            .build()));
        }

        private void mockUserContext(UUID plannerId) {
            when(plannerVoteRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(new PlannerVote(testUser.getId(), plannerId, VoteType.UP)));
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(new PlannerSubscription(testUser.getId(), plannerId)));
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId)).thenReturn(false);
        }

        @Test
        void getPublishedPlanner_WhenNewAnonymousView_BuffersView() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Assert
            assertThat(bufferedViews().stream().map(PlannerViewId::getPlannerId).toList())
                    .isEqualTo(List.of(plannerId));
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
            assertThat(result.viewCount()).isEqualTo(10);
            assertThat(result.authorUsernameEpithet()).isEqualTo(testUser.getUsernameEpithet());
            assertThat(result.authorUsernameSuffix()).isEqualTo(testUser.getUsernameSuffix());
        }

        @Test
        void getPublishedPlanner_WhenNewAuthenticatedView_BuffersView() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);
            mockUserContext(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);

            // Assert
            assertThat(bufferedViews().stream().map(PlannerViewId::getPlannerId).toList())
                    .isEqualTo(List.of(plannerId));
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
            assertThat(result.viewCount()).isEqualTo(10);
            assertThat(result.hasUpvoted()).isTrue();
            assertThat(result.isSubscribed()).isTrue();
            assertThat(result.hasReported()).isFalse();
            assertThat(result.authorUsernameEpithet()).isEqualTo(testUser.getUsernameEpithet());
            assertThat(result.authorUsernameSuffix()).isEqualTo(testUser.getUsernameSuffix());
        }

        @Test
        void getPublishedPlanner_WhenPlannerMissingOrUnpublished_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            // findPublishedAggregate filters unpublished rows at the query level
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT))
                    .withCode("PLANNER_NOT_FOUND");

            assertThat(bufferedViews()).isEmpty();
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
        }

        @Test
        void getPublishedPlanner_WhenNullUserAgent_Success() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, null);

            // Assert
            assertThat(result.id()).isEqualTo(plannerId);
            assertThat(result.viewCount()).isEqualTo(10);
            assertThat(result.content()).isNotNull();
            assertThat(result.hasUpvoted()).isFalse();
        }

        @Test
        void getPublishedPlanner_WhenAuthVsAnon_DifferentHashes() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);
            mockUserContext(plannerId);

            // Act - anonymous first
            publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Act - authenticated
            publishedQueryService.getPublishedPlanner(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);

            // Assert - hashes must differ
            List<PlannerViewId> views = bufferedViews();
            assertThat(views.size()).isEqualTo(2);
            assertThat(views.get(1).getViewerHash()).isNotEqualTo(views.get(0).getViewerHash());
        }
    }

    @Nested
    class SummaryProjectionTests {

        @Test
        void publishedListing_WhenAnonymous_ProjectsEveryCardField() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog row = catalogRow("Test Planner", false);
            UUID plannerId = row.getPlannerId();
            Instant createdAt = Instant.parse("2024-01-02T03:04:05Z");

            stubCatalogPage(pageOf(pageable, row));
            when(plannerRepository.findCoreInfoByIds(List.of(plannerId)))
                    .thenReturn(List.of(new PlannerCoreInfo(plannerId, createdAt, "W_CORP", "test1")));
            when(plannerStatsRepository.findAllById(List.of(plannerId)))
                    .thenReturn(List.of(PlannerStats.builder()
                            .plannerId(plannerId)
                            .viewCount(42)
                            .upvotes(7)
                            .commentCount(3)
                            .build()));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(1);
            PublicPlannerResponse card = result.getContent().get(0);
            assertThat(card.id()).isEqualTo(plannerId);
            assertThat(card.title()).isEqualTo("Test Planner");
            assertThat(card.category()).isEqualTo("5F");
            assertThat(card.plannerType()).isEqualTo(PlannerType.MIRROR_DUNGEON);
            assertThat(card.firstPublishedAt()).isEqualTo(row.getFirstPublishedAt());
            assertThat(card.authorUsernameEpithet()).isEqualTo("W_CORP");
            assertThat(card.authorUsernameSuffix()).isEqualTo("test1");
            assertThat(card.createdAt()).isEqualTo(createdAt);
            assertThat(card.viewCount()).isEqualTo(42);
            assertThat(card.upvotes()).isEqualTo(7);
            assertThat(card.commentCount()).isEqualTo(3L);
            assertThat(card.hasUpvoted()).isFalse();
        }
    }

    @Nested
    class SearchPlannersTests {

        @Test
        void searchPlanners_WhenAnonymousNoFilters_ReturnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog row = catalogRow("Test Planner", false);
            Page<PlannerCatalog> rowPage = new PageImpl<>(List.of(row), pageable, 1);

            stubCatalogPage(rowPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);

            // Assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            PublicPlannerResponse card = result.getContent().get(0);
            assertThat(card.id()).isEqualTo(row.getPlannerId());
            assertThat(card.title()).isEqualTo("Test Planner");
            assertThat(card.hasUpvoted()).isFalse();
        }

        @Test
        void malformedEntityFilterId_WhenParsed_IsRejectedNotSilentlyDropped() {
            // Arrange
            CatalogQuery query = new CatalogQuery(false, null, null, null,
                    Map.of(ContentEntityType.THEME_PACK, List.of("not-a-number")));

            // Act & Assert
            assertRejected(() -> publishedQueryService.searchPlanners(query, PageRequest.of(0, 10), null))
                    .withViolation("INVALID_FILTER_ID")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("INVALID_FILTER_ID"));
        }
    }
}
