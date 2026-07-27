package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.mockito.ArgumentCaptor;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.BookmarkResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.repository.UserRepository;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Unit tests for PlannerEngagementService (immutable voting and bookmark toggling).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerEngagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private org.danteplanner.backend.planner.repository.PlannerStatsRepository plannerStatsRepository;

    @Mock
    private org.danteplanner.backend.planner.service.PlannerCatalogService plannerCatalogService;

    private PlannerEngagementService engagementService;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        engagementService = new PlannerEngagementService(
                plannerRepository,
                plannerVoteRepository,
                plannerBookmarkRepository,
                plannerStatsRepository,
                plannerCatalogService,
                eventPublisher,
                new PlannerAccessGuard(userRepository, plannerRepository),
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
        // The access guard resolves the principal on every guarded path; an unstubbed
        // repository would surface as UserNotFoundException instead of the behavior under test.
        lenient().when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));

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

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("setBookmark Tests")
    class SetBookmarkTests {

        @Test
        @DisplayName("bookmarking an already-bookmarked planner leaves the bookmark in place")
        void bookmarkIdempotentStateTargeted_WhenSentTwice_StaysBookmarked() {
            Planner planner = testPlannerBuilder().published(true).build();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerBookmarkRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(new PlannerBookmark(testUser.getId(), plannerId)));

            BookmarkResponse response = engagementService.setBookmark(testUser.getId(), plannerId, true);

            assertTrue(response.bookmarked(), "a repeated bookmark must not un-bookmark");
            verify(plannerBookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("removing an absent bookmark leaves it absent")
        void bookmarkIdempotentStateTargeted_WhenRemoveRepeated_StaysUnbookmarked() {
            Planner planner = testPlannerBuilder().published(true).build();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerBookmarkRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());

            BookmarkResponse response = engagementService.setBookmark(testUser.getId(), plannerId, false);

            assertFalse(response.bookmarked());
            verify(plannerBookmarkRepository, never()).save(any(PlannerBookmark.class));
        }
    }

    @Nested
    @DisplayName("toggleBookmark Tests")
    class ToggleBookmarkTests {

        private Planner createPublishedPlanner() {
            Planner planner = testPlannerBuilder().published(true).build();
            return planner;
        }

        @Test
        @DisplayName("Should add bookmark when not bookmarked")
        void toggleBookmark_WhenNotBookmarked_AddsBookmark() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerBookmarkRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());
            when(plannerBookmarkRepository.save(any(PlannerBookmark.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            BookmarkResponse response = engagementService.toggleBookmark(testUser.getId(), plannerId);

            // Assert
            assertTrue(response.bookmarked());
            assertEquals(plannerId, response.plannerId());
            ArgumentCaptor<PlannerBookmark> bookmarkCaptor =
                    ArgumentCaptor.forClass(PlannerBookmark.class);
            verify(plannerBookmarkRepository).save(bookmarkCaptor.capture());
            assertEquals(testUser.getId(), bookmarkCaptor.getValue().getUserId());
            assertEquals(plannerId, bookmarkCaptor.getValue().getPlannerId());
            verify(plannerBookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should remove bookmark when already bookmarked")
        void toggleBookmark_WhenAlreadyBookmarked_RemovesBookmark() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerBookmark existingBookmark = new PlannerBookmark(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerBookmarkRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(existingBookmark));

            // Act
            BookmarkResponse response = engagementService.toggleBookmark(testUser.getId(), plannerId);

            // Assert
            assertFalse(response.bookmarked());
            assertEquals(plannerId, response.plannerId());
            verify(plannerBookmarkRepository).delete(existingBookmark);
            verify(plannerBookmarkRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void toggleBookmark_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findPublishedAggregate(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> engagementService.toggleBookmark(testUser.getId(), nonExistentId)
            );

            verify(plannerBookmarkRepository, never()).findByUserIdAndPlannerId(any(), any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not published")
        void toggleBookmark_WhenPlannerNotPublished_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner(); // Not published
            UUID plannerId = planner.getId();

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> engagementService.toggleBookmark(testUser.getId(), plannerId)
            );
        }
    }

    @Nested
    @DisplayName("castVote Immutability Tests")
    class CastVoteImmutabilityTests {

        private Planner createPublishedPlanner() {
            return testPlannerBuilder().published(true).build();
        }

        private org.danteplanner.backend.planner.entity.PlannerStats statsWithUpvotes(UUID plannerId, int upvotes) {
            return org.danteplanner.backend.planner.entity.PlannerStats.builder()
                    .plannerId(plannerId)
                    .upvotes(upvotes)
                    .build();
        }

        @Test
        @DisplayName("Should throw VoteAlreadyExistsException when user attempts duplicate vote")
        void castVote_WhenDuplicateVote_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            org.danteplanner.backend.planner.entity.PlannerVoteId voteId =
                new org.danteplanner.backend.planner.entity.PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(true);

            // Act & Assert
            org.danteplanner.backend.planner.exception.VoteAlreadyExistsException exception = assertThrows(
                    org.danteplanner.backend.planner.exception.VoteAlreadyExistsException.class,
                    () -> engagementService.castVote(testUser.getId(), plannerId, org.danteplanner.backend.planner.entity.VoteType.UP)
            );

            assertEquals(plannerId, exception.getPlannerId());
            assertEquals(testUser.getId(), exception.getUserId());
            verify(plannerVoteRepository, never()).save(any());
            verify(plannerStatsRepository, never()).incrementUpvotes(any());
        }

        @Test
        @DisplayName("Should throw InvalidRequestException when voteType is null")
        void castVote_WhenNullVoteType_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            // Act & Assert
            InvalidRequestException exception = assertThrows(
                    InvalidRequestException.class,
                    () -> engagementService.castVote(testUser.getId(), plannerId, null)
            );

            assertTrue(exception.getMessage().contains("Vote type cannot be null"));
            verify(plannerVoteRepository, never()).existsById(any());
            verify(plannerVoteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow first vote and create new vote record")
        void castVote_WhenFirstVote_CreatesVote() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            org.danteplanner.backend.planner.entity.PlannerVoteId voteId =
                new org.danteplanner.backend.planner.entity.PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(false);
            when(plannerVoteRepository.save(any(org.danteplanner.backend.planner.entity.PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerStatsRepository.findById(plannerId))
                    .thenReturn(Optional.of(statsWithUpvotes(plannerId, 5)), Optional.of(statsWithUpvotes(plannerId, 6)));

            // Act
            org.danteplanner.backend.planner.dto.VoteResponse response =
                    engagementService.castVote(testUser.getId(), plannerId, org.danteplanner.backend.planner.entity.VoteType.UP);

            // Assert
            assertEquals(6, response.upvoteCount());
            assertTrue(response.hasUpvoted());
            ArgumentCaptor<org.danteplanner.backend.planner.entity.PlannerVote> voteCaptor =
                    ArgumentCaptor.forClass(org.danteplanner.backend.planner.entity.PlannerVote.class);
            verify(plannerVoteRepository).save(voteCaptor.capture());
            assertEquals(testUser.getId(), voteCaptor.getValue().getUserId());
            assertEquals(plannerId, voteCaptor.getValue().getPlannerId());
            assertEquals(org.danteplanner.backend.planner.entity.VoteType.UP,
                    voteCaptor.getValue().getVoteType());
            verify(plannerStatsRepository).incrementUpvotes(plannerId);
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
