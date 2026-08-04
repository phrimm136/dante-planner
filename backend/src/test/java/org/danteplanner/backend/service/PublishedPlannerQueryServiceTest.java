package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;

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
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;

/**
 * Unit tests for PublishedPlannerQueryService (public catalog read model:
 * published/recommended listings, search, and single-planner detail).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PublishedPlannerQueryServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Mock
    private PlannerViewRecorder plannerViewRecorder;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private PlannerReportService reportService;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    private PlannerEngagementService engagementService;
    private PublishedPlannerQueryService publishedQueryService;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(userService, plannerRepository);

        // engagementService is a real collaborator wired to the mocked repositories,
        // matching how PublishedPlannerQueryService delegates isBookmarked() to it.
        engagementService = new PlannerEngagementService(
                plannerVoteRepository,
                plannerBookmarkRepository,
                plannerStatsRepository,
                plannerCatalogService,
                eventPublisher,
                accessGuard,
                reportService,
                recommendedThreshold
        );

        publishedQueryService = new PublishedPlannerQueryService(
                plannerRepository,
                catalogRepository,
                plannerVoteRepository,
                plannerBookmarkRepository,
                subscriptionService,
                reportService,
                engagementService,
                plannerViewRecorder,
                plannerStatsRepository,
                accessGuard
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
    @DisplayName("published listing")
    class PublishedListingTests {

        @Test
        void publishedListing_WhenRowsExist_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(pageOf(pageable, catalogRow("Planner 1", false), catalogRow("Planner 2", false)));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);

            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        @Test
        void publishedListing_WhenCategory_ComposesCategoryPredicate() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog inCategory = catalogRow("F5 Planner", false);
            stubCatalogPage(pageOf(pageable, inCategory));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, "5F", null, null, null), pageable, null);

            assertEquals(1, result.getTotalElements());
            PublicPlannerResponse card = result.getContent().get(0);
            assertEquals(inCategory.getPlannerId(), card.id());
            assertEquals("F5 Planner", card.title());
            assertEquals("5F", card.category());
        }

        @Test
        void publishedListing_WhenNoRows_ReturnsEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(new PageImpl<>(List.of(), pageable, 0));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(false, null, null, null, null), pageable, null);

            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
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

            assertEquals(0, card.upvotes());
            assertEquals(0, card.viewCount());
            assertEquals(0L, card.commentCount());
            assertNull(card.authorUsernameEpithet());
            assertNull(card.authorUsernameSuffix());
            assertNull(card.createdAt());
        }
    }

    @Nested
    @DisplayName("recommended listing")
    class RecommendedListingTests {

        @Test
        void recommendedListing_WhenRowsExist_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(pageOf(pageable, catalogRow("High Votes", true), catalogRow("Medium Votes", true)));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, null, null, null, null), pageable, null);

            assertEquals(2, result.getTotalElements());
        }

        @Test
        void recommendedListing_WhenCategory_ComposesCategoryPredicate() {
            Pageable pageable = PageRequest.of(0, 10);
            PlannerCatalog inCategory = catalogRow("F10 Planner", true);
            stubCatalogPage(pageOf(pageable, inCategory));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, "10F", null, null, null), pageable, null);

            assertEquals(1, result.getTotalElements());
            PublicPlannerResponse card = result.getContent().get(0);
            assertEquals(inCategory.getPlannerId(), card.id());
            assertEquals("F10 Planner", card.title());
        }

        @Test
        void recommendedListing_WhenNoRows_ReturnsEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            stubCatalogPage(new PageImpl<>(List.of(), pageable, 0));

            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    new CatalogQuery(true, null, null, null, null), pageable, null);

            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("incrementViewCount Tests")
    class IncrementViewCountTests {

        @Test
        @DisplayName("Should increment view count atomically")
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
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void incrementViewCount_WhenNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.existsActiveById(nonExistentId)).thenReturn(false);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishedQueryService.incrementViewCount(nonExistentId)
            );
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner is soft-deleted")
        void incrementViewCount_WhenDeletedPlanner_ThrowsException() {
            // Arrange: a soft-deleted planner still has a core row but is not active
            UUID deletedId = UUID.randomUUID();
            when(plannerRepository.existsActiveById(deletedId)).thenReturn(false);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishedQueryService.incrementViewCount(deletedId)
            );
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("getPublishedPlanner Tests")
    class GetPublishedPlannerTests {

        private static final String IP_ADDRESS = "192.168.1.100";
        private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        private Planner createPublishedPlannerWithId(UUID id) {
            return TestDataFactory.planner(testUser).id(id).published(true).build();
        }

        private void mockNewView(UUID plannerId) {
            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(createPublishedPlannerWithId(plannerId)));
            when(plannerStatsRepository.findById(plannerId))
                    .thenReturn(Optional.of(PlannerStats.builder()
                            .plannerId(plannerId)
                            .viewCount(10)
                            .build()));
        }

        /**
         * Each of the four context flags carries a distinct value, so a response that crosses
         * two of them fails rather than matching by coincidence.
         */
        private void mockUserContext(UUID plannerId) {
            when(plannerVoteRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(new PlannerVote(testUser.getId(), plannerId, VoteType.UP)));
            when(plannerBookmarkRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);
            when(subscriptionService.isSubscribed(testUser.getId(), plannerId)).thenReturn(true);
            when(reportService.hasReported(testUser.getId(), plannerId)).thenReturn(false);
        }

        @Test
        @DisplayName("Should buffer view and return pre-request count for anonymous visitor")
        void getPublishedPlanner_WhenNewAnonymousView_BuffersView() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Assert
            // A buffered view has no state form at this tier: it reaches planner_view only when
            // PlannerViewRecorder.flush() commits, which needs a containerized test.
            verify(plannerViewRecorder).record(eq(plannerId), any(), any());
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
            assertEquals(10, result.viewCount());
        }

        @Test
        @DisplayName("Should buffer view and return pre-request count for authenticated visitor")
        void getPublishedPlanner_WhenNewAuthenticatedView_BuffersView() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);
            mockUserContext(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);

            // Assert
            // A buffered view has no state form at this tier: it reaches planner_view only when
            // PlannerViewRecorder.flush() commits, which needs a containerized test.
            verify(plannerViewRecorder).record(eq(plannerId), any(), any());
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
            assertEquals(10, result.viewCount());
            assertTrue(result.hasUpvoted());
            assertFalse(result.isBookmarked());
            assertTrue(result.isSubscribed());
            assertFalse(result.hasReported());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException for unpublished planner")
        void getPublishedPlanner_WhenUnpublishedPlanner_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            // findPublishedAggregate filters unpublished rows at the query level
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT));

            verify(plannerViewRecorder, never()).record(any(), any(), any());
            verify(plannerStatsRepository, never()).incrementViewCountBy(any(), anyInt());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException for non-existent planner")
        void getPublishedPlanner_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT));
        }

        @Test
        @DisplayName("Should handle null User-Agent gracefully")
        void getPublishedPlanner_WhenNullUserAgent_Success() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);

            // Act
            PublishedPlannerDetailResponse result = assertDoesNotThrow(() ->
                    publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, null));

            // Assert
            assertEquals(plannerId, result.id());
            assertEquals(10, result.viewCount());
            assertNotNull(result.content());
            assertNull(result.hasUpvoted());
            assertNull(result.isBookmarked());
        }

        @Test
        @DisplayName("Should use different viewer hashes for authenticated vs anonymous")
        void getPublishedPlanner_WhenAuthVsAnon_DifferentHashes() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);
            mockUserContext(plannerId);

            // The viewer hash never reaches the response; comparing the two forms through state
            // would mean reading the recorded planner_view rows in a containerized test.
            ArgumentCaptor<String> anonHash = ArgumentCaptor.forClass(String.class);

            // Act - anonymous first
            publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT);
            verify(plannerViewRecorder).record(eq(plannerId), anonHash.capture(), any());

            // Reset the recorder for the authenticated call
            reset(plannerViewRecorder);
            ArgumentCaptor<String> authHash = ArgumentCaptor.forClass(String.class);

            // Act - authenticated
            publishedQueryService.getPublishedPlanner(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);
            verify(plannerViewRecorder).record(eq(plannerId), authHash.capture(), any());

            // Assert - hashes must differ
            assertNotEquals(anonHash.getValue(), authHash.getValue());
        }
    }

    @Nested
    @DisplayName("summary projection")
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

            assertEquals(1, result.getTotalElements());
            PublicPlannerResponse card = result.getContent().get(0);
            assertEquals(plannerId, card.id());
            assertEquals("Test Planner", card.title());
            assertEquals("5F", card.category());
            assertEquals(PlannerType.MIRROR_DUNGEON, card.plannerType());
            assertEquals(row.getFirstPublishedAt(), card.firstPublishedAt());
            assertEquals("W_CORP", card.authorUsernameEpithet());
            assertEquals("test1", card.authorUsernameSuffix());
            assertEquals(createdAt, card.createdAt());
            assertEquals(42, card.viewCount());
            assertEquals(7, card.upvotes());
            assertEquals(3L, card.commentCount());
            assertNull(card.hasUpvoted());
            assertNull(card.isBookmarked());
        }
    }

    @Nested
    @DisplayName("searchPlanners Tests")
    class SearchPlannersTests {

        @Test
        @DisplayName("Should search planners via specification and map for anonymous user")
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
            assertEquals(1, result.getTotalElements());
            PublicPlannerResponse card = result.getContent().get(0);
            assertEquals(row.getPlannerId(), card.id());
            assertEquals("Test Planner", card.title());
            assertNull(card.hasUpvoted());
            assertNull(card.isBookmarked());
        }

        @Test
        @DisplayName("Should reject a non-numeric entity filter id rather than empty the page")
        void malformedEntityFilterId_WhenParsed_IsRejectedNotSilentlyDropped() {
            // Arrange
            CatalogQuery query = new CatalogQuery(false, null, null, null,
                    Map.of(ContentEntityType.THEME_PACK, List.of("not-a-number")));

            // Act
            PlannerValidationException thrown = assertThrows(PlannerValidationException.class,
                    () -> publishedQueryService.searchPlanners(query, PageRequest.of(0, 10), null));

            // Assert
            assertEquals("INVALID_FILTER_ID", thrown.getErrorCode());
        }
    }
}
