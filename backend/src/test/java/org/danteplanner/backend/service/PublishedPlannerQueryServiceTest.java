package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
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
import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private PlannerSubscriptionRepository subscriptionRepository;

    @Mock
    private PlannerReportRepository reportRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    private PublishedPlannerQueryService publishedQueryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(null, plannerRepository);

        publishedQueryService = new PublishedPlannerQueryService(
                plannerRepository,
                catalogRepository,
                plannerVoteRepository,
                new PlannerSubscriptionService(subscriptionRepository, accessGuard, null),
                new PlannerReportService(reportRepository, null, null),
                null,
                plannerStatsRepository,
                accessGuard,
                new CatalogReadValidator()
        );

        testUser = TestDataFactory.unsavedUser(1L);
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
                    new CatalogQuery(false, null, null, null, null), pageable, null).value();

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().size()).isEqualTo(2);
        }

        @Test
        void publishedListing_WhenCategory_ComposesCategoryPredicate() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog inCategory = catalogRow("F5 Planner", false);
            stubCatalogPage(pageOf(pageable, inCategory));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, "5F", null, null, null), pageable, null).value();

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
                    new CatalogQuery(false, null, null, null, null), pageable, null).value();

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
                    new CatalogQuery(false, null, null, null, null), pageable, null).value()
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
                    new CatalogQuery(true, null, null, null, null), pageable, null).value();

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void recommendedListing_WhenCategory_ComposesCategoryPredicate() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog inCategory = catalogRow("F10 Planner", true);
            stubCatalogPage(pageOf(pageable, inCategory));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, "10F", null, null, null), pageable, null).value();

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
                    new CatalogQuery(true, null, null, null, null), pageable, null).value();

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    class GetPublishedPlannerTests {

        private Planner createPublishedPlannerWithId(UUID id) {
            return TestDataFactory.planner(testUser).id(id).published(true).build();
        }

        private void mockPublished(UUID plannerId) {
            when(plannerRepository.findAggregateWithAuthor(plannerId))
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
        void getPublishedPlanner_WhenAnonymous_ServesTheDetailUnderItsEntityAndAuthorScopes() {
            UUID plannerId = UUID.randomUUID();
            mockPublished(plannerId);

            ScopedRead<PublishedPlannerDetailResponse> read = publishedQueryService.getPublishedPlanner(plannerId, null);

            assertThat(read.scopes()).containsExactlyInAnyOrder(
                    new FreshnessScope.Entity(plannerId), new FreshnessScope.Author(testUser.getId()));
            assertThat(read.value().viewCount()).isEqualTo(10);
            assertThat(read.value().authorUsernameEpithet()).isEqualTo(testUser.getUsernameEpithet());
            assertThat(read.value().authorUsernameSuffix()).isEqualTo(testUser.getUsernameSuffix());
        }

        @Test
        void getPublishedPlanner_WhenAuthenticated_ServesTheViewerFlags() {
            UUID plannerId = UUID.randomUUID();
            mockPublished(plannerId);
            mockUserContext(plannerId);

            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, testUser.getId()).value();

            assertThat(result.viewCount()).isEqualTo(10);
            assertThat(result.hasUpvoted()).isTrue();
            assertThat(result.isSubscribed()).isTrue();
            assertThat(result.hasReported()).isFalse();
            assertThat(result.authorUsernameEpithet()).isEqualTo(testUser.getUsernameEpithet());
            assertThat(result.authorUsernameSuffix()).isEqualTo(testUser.getUsernameSuffix());
        }

        @Test
        void getPublishedPlanner_WhenNoRow_ThrowsAbsent() {
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findAggregateWithAuthor(plannerId)).thenReturn(Optional.empty());

            assertRejected(() -> publishedQueryService.getPublishedPlanner(plannerId, null))
                    .withCode("PLANNER_NOT_FOUND")
                    .isInstanceOfSatisfying(PlannerNotFoundException.class,
                            thrown -> assertThat(thrown.miss()).isEqualTo(new PlannerMiss.Absent(plannerId)));
        }

        @Test
        void getPublishedPlanner_WhenRowIsUnpublished_ThrowsHiddenNamingTheOwner() {
            UUID plannerId = UUID.randomUUID();
            Planner draft = TestDataFactory.planner(testUser).id(plannerId).published(false).build();
            when(plannerRepository.findAggregateWithAuthor(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(draft, TestDataFactory.profileOf(testUser))));

            assertRejected(() -> publishedQueryService.getPublishedPlanner(plannerId, null))
                    .withCode("PLANNER_NOT_FOUND")
                    .isInstanceOfSatisfying(PlannerNotFoundException.class, thrown -> {
                        assertThat(thrown.miss()).isEqualTo(new PlannerMiss.Hidden(plannerId, testUser.getId()));
                        assertThat(thrown.scopes()).containsExactlyInAnyOrder(
                                new FreshnessScope.Entity(plannerId), new FreshnessScope.Author(testUser.getId()));
                    });
        }
    }

    @Nested
    class SummaryProjectionTests {

        private static final long AUTHOR_ID = 77L;

        @Test
        void publishedListing_WhenAnonymous_ProjectsEveryCardField() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog row = catalogRow("Test Planner", false);
            UUID plannerId = row.getPlannerId();
            Instant createdAt = Instant.parse("2024-01-02T03:04:05Z");

            stubCatalogPage(pageOf(pageable, row));
            when(plannerRepository.findCoreInfoByIds(List.of(plannerId)))
                    .thenReturn(List.of(new PlannerCoreInfo(plannerId, AUTHOR_ID, createdAt, "W_CORP", "test1")));
            when(plannerStatsRepository.findAllById(List.of(plannerId)))
                    .thenReturn(List.of(PlannerStats.builder()
                            .plannerId(plannerId)
                            .viewCount(42)
                            .upvotes(7)
                            .commentCount(3)
                            .build()));

            ScopedRead<Page<PublicPlannerResponse>> read = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);
            Page<PublicPlannerResponse> result = read.value();

            assertThat(read.scopes()).containsExactlyInAnyOrder(
                    new FreshnessScope.PublicCatalog(), new FreshnessScope.Author(AUTHOR_ID));
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
                    new CatalogQuery(false, null, null, null, null), pageable, null).value();

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
