package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerEngagementService;

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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerEngagementService (immutable voting and bookmark toggling).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerEngagementServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
                eventPublisher,
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
    @DisplayName("toggleBookmark Tests")
    class ToggleBookmarkTests {

        private Planner createPublishedPlanner() {
            Planner planner = testPlannerBuilder().published(true).build();
            return planner;
        }

        @Test
        @DisplayName("Should add bookmark when not bookmarked")
        void toggleBookmark_NotBookmarked_AddsBookmark() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
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
            verify(plannerBookmarkRepository).save(any(PlannerBookmark.class));
            verify(plannerBookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should remove bookmark when already bookmarked")
        void toggleBookmark_AlreadyBookmarked_RemovesBookmark() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerBookmark existingBookmark = new PlannerBookmark(testUser.getId(), plannerId);

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
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
        void toggleBookmark_PlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(nonExistentId))
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
        void toggleBookmark_PlannerNotPublished_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner(); // Not published
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
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
            Planner planner = testPlannerBuilder().published(true).build();
            planner.setUpvotes(5);
            return planner;
        }

        @Test
        @DisplayName("Should throw VoteAlreadyExistsException when user attempts duplicate vote")
        void castVote_DuplicateVote_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            org.danteplanner.backend.planner.entity.PlannerVoteId voteId =
                new org.danteplanner.backend.planner.entity.PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
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
            verify(plannerRepository, never()).incrementUpvotes(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when voteType is null")
        void castVote_NullVoteType_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdForUpdate(plannerId))
                    .thenReturn(Optional.of(planner));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> engagementService.castVote(testUser.getId(), plannerId, null)
            );

            assertTrue(exception.getMessage().contains("Vote type cannot be null"));
            verify(plannerVoteRepository, never()).existsById(any());
            verify(plannerVoteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow first vote and create new vote record")
        void castVote_FirstVote_CreatesVote() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            Planner updatedPlanner = createPublishedPlanner();
            updatedPlanner.setId(plannerId);
            updatedPlanner.setUpvotes(6);
            org.danteplanner.backend.planner.entity.PlannerVoteId voteId =
                new org.danteplanner.backend.planner.entity.PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(false);
            when(plannerVoteRepository.save(any(org.danteplanner.backend.planner.entity.PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(updatedPlanner));

            // Act
            org.danteplanner.backend.planner.dto.VoteResponse response =
                    engagementService.castVote(testUser.getId(), plannerId, org.danteplanner.backend.planner.entity.VoteType.UP);

            // Assert
            assertEquals(6, response.upvoteCount());
            assertTrue(response.hasUpvoted());
            verify(plannerVoteRepository).save(any(org.danteplanner.backend.planner.entity.PlannerVote.class));
            verify(plannerRepository).incrementUpvotes(plannerId);
        }
    }

    @Nested
    @DisplayName("isBookmarked Tests")
    class IsBookmarkedTests {

        @Test
        @DisplayName("Should return true when bookmark exists")
        void isBookmarked_Exists_ReturnsTrue() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerBookmarkRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act & Assert
            assertTrue(engagementService.isBookmarked(testUser.getId(), plannerId));
        }

        @Test
        @DisplayName("Should return false when bookmark does not exist")
        void isBookmarked_NotExists_ReturnsFalse() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerBookmarkRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);

            // Act & Assert
            assertFalse(engagementService.isBookmarked(testUser.getId(), plannerId));
        }
    }
}
