package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserAccountLifecycleService.
 *
 * <p>Tests soft-delete, reactivation, and hard-delete functionality
 * with all dependencies mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAccountLifecycleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private org.danteplanner.backend.repository.PlannerCommentRepository plannerCommentRepository;

    @Mock
    private org.danteplanner.backend.repository.PlannerCommentVoteRepository plannerCommentVoteRepository;

    private UserAccountLifecycleService lifecycleService;

    private static final int GRACE_PERIOD_DAYS = 30;

    private User testUser;

    @BeforeEach
    void setUp() {
        lifecycleService = new UserAccountLifecycleService(
                userRepository,
                plannerVoteRepository,
                plannerCommentRepository,
                plannerCommentVoteRepository,
                GRACE_PERIOD_DAYS
        );

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();
    }

    @Nested
    @DisplayName("deleteAccount Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should set deletedAt and scheduledDate on first deletion")
        void deleteAccount_setsDeletedAtAndScheduledDate() {
            // Arrange
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Instant scheduledDate = lifecycleService.deleteAccount(testUser.getId());

            // Assert
            assertNotNull(scheduledDate);
            assertTrue(testUser.isDeleted());
            assertNotNull(testUser.getDeletedAt());
            assertNotNull(testUser.getPermanentDeleteScheduledAt());

            // Verify scheduled date is approximately 30 days from now
            Instant expectedScheduleMin = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS - 1));
            Instant expectedScheduleMax = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS + 1));
            assertTrue(scheduledDate.isAfter(expectedScheduleMin));
            assertTrue(scheduledDate.isBefore(expectedScheduleMax));

            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should be idempotent and return existing scheduled date")
        void deleteAccount_idempotent_returnsExistingScheduledDate() {
            // Arrange - user already deleted
            Instant existingScheduledAt = Instant.now().plus(Duration.ofDays(25));
            testUser.softDelete(existingScheduledAt);

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            Instant scheduledDate = lifecycleService.deleteAccount(testUser.getId());

            // Assert
            assertEquals(existingScheduledAt, scheduledDate);
            verify(userRepository, never()).save(any()); // No save on idempotent call
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void deleteAccount_userNotFound_throwsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> lifecycleService.deleteAccount(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reactivateAccount Tests")
    class ReactivateAccountTests {

        @Test
        @DisplayName("Should clear deletedAt and scheduledDate on reactivation")
        void reactivateAccount_clearsDeletedAtAndScheduledDate() {
            // Arrange - user is soft-deleted
            testUser.softDelete(Instant.now().plus(Duration.ofDays(30)));
            assertTrue(testUser.isDeleted());

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            assertFalse(testUser.isDeleted());
            assertNull(testUser.getDeletedAt());
            assertNull(testUser.getPermanentDeleteScheduledAt());

            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void reactivateAccount_userNotFound_throwsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> lifecycleService.reactivateAccount(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should be idempotent for non-deleted user")
        void reactivateAccount_nonDeletedUser_noOp() {
            // Arrange - user is not deleted
            assertFalse(testUser.isDeleted());

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            assertFalse(testUser.isDeleted());
            verify(userRepository, never()).save(any()); // Idempotent: no save for already-active user
        }
    }

    @Nested
    @DisplayName("performHardDelete Tests")
    class PerformHardDeleteTests {

        @Test
        @DisplayName("Should reassign votes to sentinel and delete user")
        void performHardDelete_reassignsVotesAndDeletesUser() {
            // Arrange
            when(plannerVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(5);
            when(plannerCommentVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(3);
            when(plannerCommentRepository.reassignCommentsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(2);
            doNothing().when(userRepository).delete(testUser);

            // Act
            lifecycleService.performHardDelete(testUser);

            // Assert - verify order of operations
            var inOrder = inOrder(plannerVoteRepository, plannerCommentVoteRepository, plannerCommentRepository, userRepository);
            inOrder.verify(plannerVoteRepository).reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(plannerCommentVoteRepository).reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(plannerCommentRepository).reassignCommentsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should use correct sentinel user ID")
        void performHardDelete_usesCorrectSentinelId() {
            // Arrange
            ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> sentinelIdCaptor = ArgumentCaptor.forClass(Long.class);
            when(plannerVoteRepository.reassignUserVotes(userIdCaptor.capture(), sentinelIdCaptor.capture()))
                    .thenReturn(3);
            when(plannerCommentVoteRepository.reassignUserVotes(any(), any()))
                    .thenReturn(2);
            when(plannerCommentRepository.reassignCommentsToSentinel(any(), any()))
                    .thenReturn(1);
            doNothing().when(userRepository).delete(testUser);

            // Act
            lifecycleService.performHardDelete(testUser);

            // Assert
            assertEquals(testUser.getId(), userIdCaptor.getValue());
            assertEquals(0L, sentinelIdCaptor.getValue()); // SENTINEL_USER_ID = 0
        }

        @Test
        @DisplayName("Should reassign both planner votes and comment votes")
        void performHardDelete_reassignsBothVoteTypes() {
            // Arrange
            when(plannerVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(5);
            when(plannerCommentVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(3);
            when(plannerCommentRepository.reassignCommentsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(2);
            doNothing().when(userRepository).delete(testUser);

            // Act
            lifecycleService.performHardDelete(testUser);

            // Assert
            verify(plannerVoteRepository).reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            verify(plannerCommentVoteRepository).reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
        }

        @Test
        @DisplayName("Should handle zero votes reassignment gracefully")
        void performHardDelete_zeroVotes_stillDeletesUser() {
            // Arrange
            when(plannerVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(0);
            when(plannerCommentVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(0);
            when(plannerCommentRepository.reassignCommentsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(0);
            doNothing().when(userRepository).delete(testUser);

            // Act
            lifecycleService.performHardDelete(testUser);

            // Assert
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should reassign comments before deleting user")
        void performHardDelete_reassignsCommentsBeforeDelete() {
            // Arrange
            when(plannerVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(2);
            when(plannerCommentVoteRepository.reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(1);
            when(plannerCommentRepository.reassignCommentsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID))
                    .thenReturn(4);
            doNothing().when(userRepository).delete(testUser);

            // Act
            lifecycleService.performHardDelete(testUser);

            // Assert - verify order includes comment reassignment
            var inOrder = inOrder(plannerVoteRepository, plannerCommentVoteRepository, plannerCommentRepository, userRepository);
            inOrder.verify(plannerVoteRepository).reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(plannerCommentVoteRepository).reassignUserVotes(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(plannerCommentRepository).reassignCommentsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(userRepository).delete(testUser);
        }
    }
}
