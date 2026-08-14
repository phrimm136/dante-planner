package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.mockito.ArgumentCaptor;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.service.UserService;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyLong;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.planner.validation.VoteUniquenessValidator;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.entity.VoteType;

/**
 * Unit tests for PlannerEngagementService (immutable voting and bookmark reads).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerEngagementServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Mock
    private DomainEventRecorder domainEventRecorder;

    @Mock
    private PlannerStatsService plannerStatsService;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    @Mock
    private PlannerReportService reportService;

    private PlannerEngagementService engagementService;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        engagementService = new PlannerEngagementService(
                plannerVoteRepository,
                plannerBookmarkRepository,
                plannerStatsService,
                plannerCatalogService,
                domainEventRecorder,
                new PlannerAccessGuard(userService, plannerRepository),
                reportService,
                new VoteUniquenessValidator(),
                recommendedThreshold
        );

        testUser = TestDataFactory.unsavedUser(1L);
        // The access guard resolves the principal on every guarded path; an unstubbed
        // repository would surface as UserNotFoundException instead of the behavior under test.
        lenient().when(userService.findById(anyLong())).thenReturn(testUser);

    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser)
                .title("Test Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{\"data\": \"test\"}")
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON);
    }

    @Nested
    @DisplayName("castVote Immutability Tests")
    class CastVoteImmutabilityTests {

        private Planner createPublishedPlanner() {
            return testPlannerBuilder().published(true).build();
        }

        @Test
        @DisplayName("Should throw VoteAlreadyExistsException when user attempts duplicate vote")
        void castVote_WhenDuplicateVote_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerVoteId voteId =
                new PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(true);

            // Act & Assert
            VoteAlreadyExistsException exception = assertThrows(
                    VoteAlreadyExistsException.class,
                    () -> engagementService.castVote(testUser.getId(), plannerId, VoteType.UP)
            );

            assertEquals(plannerId, exception.getPlannerId());
            assertEquals(testUser.getId(), exception.getUserId());
            verify(plannerVoteRepository, never()).insert(any());
            verify(plannerStatsService, never()).incrementUpvotes(any());
        }

        @Test
        @DisplayName("Should allow first vote and create new vote record")
        void castVote_WhenFirstVote_CreatesVote() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerVoteId voteId =
                new PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerStatsService.upvotesOf(plannerId)).thenReturn(6);

            // Act
            VoteResponse response =
                    engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            // Assert
            assertEquals(6, response.upvoteCount());
            assertTrue(response.hasUpvoted());
            ArgumentCaptor<PlannerVote> voteCaptor =
                    ArgumentCaptor.forClass(PlannerVote.class);
            verify(plannerVoteRepository).insert(voteCaptor.capture());
            assertEquals(testUser.getId(), voteCaptor.getValue().getUserId());
            assertEquals(plannerId, voteCaptor.getValue().getPlannerId());
            assertEquals(VoteType.UP,
                    voteCaptor.getValue().getVoteType());
            verify(plannerStatsService).incrementUpvotes(plannerId);
        }

        @Test
        @DisplayName("crossing the threshold records one PLANNER_RECOMMENDED event")
        void castVote_WhenCrossingTheThreshold_RecordsTheRecommendedEvent() {
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(any(PlannerVoteId.class))).thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerStatsService.upvotesOf(plannerId)).thenReturn(recommendedThreshold);
            when(plannerStatsService.trySetRecommendedNotified(plannerId, recommendedThreshold))
                    .thenReturn(1);

            engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            verify(domainEventRecorder).recordDomainEvent(
                    DomainEventType.PLANNER_RECOMMENDED, plannerId,
                    Map.of("ownerId", planner.getUser().getId()));
        }

        @Test
        @DisplayName("a vote the latch refuses records nothing, so one crossing owes one event")
        void castVote_WhenAnotherVoteAlreadyLatched_RecordsNothing() {
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(any(PlannerVoteId.class))).thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerStatsService.upvotesOf(plannerId)).thenReturn(recommendedThreshold);
            when(plannerStatsService.trySetRecommendedNotified(plannerId, recommendedThreshold))
                    .thenReturn(0);

            engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            verifyNoInteractions(domainEventRecorder);
        }

        @Test
        @DisplayName("a vote below the threshold records nothing")
        void castVote_WhenBelowTheThreshold_RecordsNothing() {
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(any(PlannerVoteId.class))).thenReturn(false);
            when(plannerVoteRepository.insert(any(PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerStatsService.upvotesOf(plannerId)).thenReturn(recommendedThreshold - 1);

            engagementService.castVote(testUser.getId(), plannerId, VoteType.UP);

            verifyNoInteractions(domainEventRecorder);
        }
    }

    @Nested
    @DisplayName("isBookmarked Tests")
    class IsBookmarkedTests {

        @Test
        @DisplayName("Should return true when bookmark exists")
        void isBookmarked_WhenExists_ReturnsTrue() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerBookmarkRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act & Assert
            assertTrue(engagementService.isBookmarked(testUser.getId(), plannerId));
        }

        @Test
        @DisplayName("Should return false when bookmark does not exist")
        void isBookmarked_WhenNotExists_ReturnsFalse() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerBookmarkRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);

            // Act & Assert
            assertFalse(engagementService.isBookmarked(testUser.getId(), plannerId));
        }
    }
}
