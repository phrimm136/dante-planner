package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;

import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PublishedPlannerQueryService (public catalog read model:
 * published/recommended listings, search, and single-planner detail).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PublishedPlannerQueryServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Mock
    private PlannerViewRecorder plannerViewRecorder;

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private PlannerReportService reportService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlannerEngagementService engagementService;
    private PublishedPlannerQueryService publishedQueryService;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // engagementService is a real collaborator wired to the mocked repositories,
        // matching how PublishedPlannerQueryService delegates isBookmarked() to it.
        engagementService = new PlannerEngagementService(
                plannerRepository,
                plannerVoteRepository,
                plannerBookmarkRepository,
                eventPublisher,
                recommendedThreshold
        );

        publishedQueryService = new PublishedPlannerQueryService(
                plannerRepository,
                plannerVoteRepository,
                plannerBookmarkRepository,
                commentRepository,
                subscriptionService,
                reportService,
                engagementService,
                plannerViewRecorder,
                recommendedThreshold
        );

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();
    }

    private Planner.PlannerBuilder testPlannerBuilder() {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{\"data\": \"test\"}")
                .syncVersion(1L)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .savedAt(Instant.now());
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("getPublishedPlanners Tests")
    class GetPublishedPlannersTests {

        private Planner createPublishedPlanner(String title) {
            return testPlannerBuilder().title(title).published(true).build();
        }

        @Test
        @DisplayName("Should return paginated published planners")
        void getPublishedPlanners_WhenCalled_ReturnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Planner> planners = List.of(
                    createPublishedPlanner("Planner 1"),
                    createPublishedPlanner("Planner 2")
            );
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 2);

            when(plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.getPublishedPlanners(pageable, null);

            // Assert
            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        @Test
        @DisplayName("Should filter by category when provided")
        void getPublishedPlanners_WithCategory_FiltersResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            String category = "5F";
            List<Planner> planners = List.of(createPublishedPlanner("F5 Planner"));
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 1);

            when(plannerRepository.findByPublishedTrueAndCategoryAndDeletedAtIsNull(category, pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.getPublishedPlanners(pageable, category);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findByPublishedTrueAndCategoryAndDeletedAtIsNull(category, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no published planners")
        void getPublishedPlanners_NoPlanners_ReturnsEmpty() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Planner> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.getPublishedPlanners(pageable, null);

            // Assert
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("getRecommendedPlanners Tests")
    class GetRecommendedPlannersTests {

        private Planner createRecommendedPlanner(String title, int upvotes) {
            return testPlannerBuilder().title(title).published(true).upvotes(upvotes).build();
        }

        @Test
        @DisplayName("Should return planners meeting threshold")
        void getRecommendedPlanners_WhenCalled_ReturnsQualifyingPlanners() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Planner> planners = List.of(
                    createRecommendedPlanner("High Votes", 15),
                    createRecommendedPlanner("Medium Votes", 12)
            );
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 2);

            when(plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.getRecommendedPlanners(pageable, null);

            // Assert
            assertEquals(2, result.getTotalElements());
        }

        @Test
        @DisplayName("Should filter by category when provided")
        void getRecommendedPlanners_WithCategory_FiltersResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            String category = "10F";
            List<Planner> planners = List.of(createRecommendedPlanner("F10 Planner", 20));
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 1);

            when(plannerRepository.findRecommendedPlannersByCategory(recommendedThreshold, category, pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.getRecommendedPlanners(pageable, category);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findRecommendedPlannersByCategory(
                    recommendedThreshold, category, pageable);
        }

        @Test
        @DisplayName("Should return empty when no planners meet threshold")
        void getRecommendedPlanners_NoneQualify_ReturnsEmpty() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Planner> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.getRecommendedPlanners(pageable, null);

            // Assert
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("incrementViewCount Tests")
    class IncrementViewCountTests {

        @Test
        @DisplayName("Should increment view count atomically")
        void incrementViewCount_Success_IncrementsCount() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.incrementViewCount(plannerId)).thenReturn(1);

            // Act
            publishedQueryService.incrementViewCount(plannerId);

            // Assert
            verify(plannerRepository).incrementViewCount(plannerId);
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void incrementViewCount_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.incrementViewCount(nonExistentId)).thenReturn(0);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishedQueryService.incrementViewCount(nonExistentId)
            );
        }

        @Test
        @DisplayName("Should handle deleted planner (returns 0 rows updated)")
        void incrementViewCount_DeletedPlanner_ThrowsException() {
            // Arrange
            UUID deletedPlannerId = UUID.randomUUID();
            when(plannerRepository.incrementViewCount(deletedPlannerId)).thenReturn(0);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishedQueryService.incrementViewCount(deletedPlannerId)
            );
        }
    }

    @Nested
    @DisplayName("getPublishedPlanner Tests")
    class GetPublishedPlannerTests {

        private static final String IP_ADDRESS = "192.168.1.100";
        private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        private Planner createPublishedPlanner() {
            Planner planner = testPlannerBuilder().published(true).build();
            planner.setViewCount(10);
            return planner;
        }

        private void mockNewView(UUID plannerId) {
            when(plannerRepository.findById(plannerId))
                    .thenReturn(Optional.of(createPublishedPlannerWithId(plannerId)));
            when(commentRepository.countByPlannerIdAndDeletedAtIsNull(plannerId)).thenReturn(0L);
        }

        private Planner createPublishedPlannerWithId(UUID id) {
            Planner planner = createPublishedPlanner();
            planner.setId(id);
            return planner;
        }

        private void mockUserContext(UUID plannerId) {
            when(plannerVoteRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());
            when(plannerBookmarkRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);
            when(subscriptionService.isSubscribed(testUser.getId(), plannerId)).thenReturn(false);
            when(reportService.hasReported(testUser.getId(), plannerId)).thenReturn(false);
        }

        @Test
        @DisplayName("Should buffer view and return pre-request count for anonymous visitor")
        void getPublishedPlanner_NewAnonymousView_BuffersView() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Assert
            verify(plannerViewRecorder).record(eq(plannerId), any(), any());
            verify(plannerRepository, never()).incrementViewCount(any());
            assertEquals(10, result.viewCount());
        }

        @Test
        @DisplayName("Should buffer view and return pre-request count for authenticated visitor")
        void getPublishedPlanner_NewAuthenticatedView_BuffersView() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);
            mockUserContext(plannerId);

            // Act
            PublishedPlannerDetailResponse result =
                    publishedQueryService.getPublishedPlanner(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);

            // Assert
            verify(plannerViewRecorder).record(eq(plannerId), any(), any());
            verify(plannerRepository, never()).incrementViewCount(any());
            assertEquals(10, result.viewCount());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException for unpublished planner")
        void getPublishedPlanner_UnpublishedPlanner_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            Planner unpublished = createTestPlanner();
            unpublished.setId(plannerId);
            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(unpublished));

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT));

            verify(plannerViewRecorder, never()).record(any(), any(), any());
            verify(plannerRepository, never()).incrementViewCount(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException for non-existent planner")
        void getPublishedPlanner_PlannerNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findById(plannerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, USER_AGENT));
        }

        @Test
        @DisplayName("Should handle null User-Agent gracefully")
        void getPublishedPlanner_NullUserAgent_Success() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);

            // Act & Assert - should not throw
            assertDoesNotThrow(() ->
                    publishedQueryService.getPublishedPlanner(plannerId, null, IP_ADDRESS, null));

            verify(plannerViewRecorder).record(eq(plannerId), any(), any());
        }

        @Test
        @DisplayName("Should use different viewer hashes for authenticated vs anonymous")
        void getPublishedPlanner_AuthVsAnon_DifferentHashes() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            mockNewView(plannerId);
            mockUserContext(plannerId);

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
    @DisplayName("getPublishedPlanners with user context Tests")
    class GetPublishedPlannersWithContextTests {

        @Test
        @DisplayName("Should list published planners without search for anonymous user")
        void getPublishedPlanners_NoSearchAnonymous_ReturnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Planner planner = testPlannerBuilder().published(true).build();
            Page<Planner> plannerPage = new PageImpl<>(List.of(planner), pageable, 1);

            when(plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable)).thenReturn(plannerPage);
            when(commentRepository.countByPlannerIdsGrouped(anyList())).thenReturn(List.of());

            // Act
            Page<PublicPlannerResponse> result =
                    publishedQueryService.getPublishedPlanners(pageable, null, null, null);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findByPublishedTrueAndDeletedAtIsNull(pageable);
        }
    }

    @Nested
    @DisplayName("getRecommendedPlanners with user context Tests")
    class GetRecommendedPlannersWithContextTests {

        @Test
        @DisplayName("Should list recommended planners without search for anonymous user")
        void getRecommendedPlanners_NoSearchAnonymous_ReturnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Planner planner = testPlannerBuilder().published(true).upvotes(20).build();
            Page<Planner> plannerPage = new PageImpl<>(List.of(planner), pageable, 1);

            when(plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable)).thenReturn(plannerPage);
            when(commentRepository.countByPlannerIdsGrouped(anyList())).thenReturn(List.of());

            // Act
            Page<PublicPlannerResponse> result =
                    publishedQueryService.getRecommendedPlanners(pageable, null, null, null);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findRecommendedPlanners(recommendedThreshold, pageable);
        }
    }

    @Nested
    @DisplayName("searchPlanners Tests")
    class SearchPlannersTests {

        @Test
        @DisplayName("Should search planners via specification and map for anonymous user")
        void searchPlanners_AnonymousNoFilters_ReturnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Planner planner = testPlannerBuilder().published(true).build();
            Page<Planner> plannerPage = new PageImpl<>(List.of(planner), pageable, 1);
            Specification<Planner> baseSpec = (root, query, cb) -> null;

            when(plannerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(plannerPage);
            when(commentRepository.countByPlannerIdsGrouped(anyList())).thenReturn(List.of());

            // Act
            Page<PublicPlannerResponse> result = publishedQueryService.searchPlanners(
                    baseSpec, pageable, null, null, null, null, null, null, null, null);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findAll(any(Specification.class), eq(pageable));
        }
    }
}
