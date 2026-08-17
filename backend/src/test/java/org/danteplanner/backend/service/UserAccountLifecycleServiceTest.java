package org.danteplanner.backend.service;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.comment.service.CommentAccountPurgeService;
import org.danteplanner.backend.moderation.service.ModerationAccountPurgeService;
import org.danteplanner.backend.planner.service.PlannerAccountPurgeService;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private PlannerAccountPurgeService plannerAccountPurgeService;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    @Mock
    private CommentAccountPurgeService commentAccountPurgeService;

    @Mock
    private ModerationAccountPurgeService moderationAccountPurgeService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private UserAccountLifecycleService lifecycleService;

    private static final int GRACE_PERIOD_DAYS = 30;

    private User testUser;

    @BeforeEach
    void setUp() {
        lifecycleService = new UserAccountLifecycleService(
                userRepository,
                plannerAccountPurgeService,
                plannerCatalogService,
                commentAccountPurgeService,
                moderationAccountPurgeService,
                tokenBlacklistService,
                GRACE_PERIOD_DAYS
        );

        testUser = TestDataFactory.unsavedUser(123L);
    }

    @Nested
    @DisplayName("deleteAccount Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should set deletedAt and scheduledDate on first deletion")
        void deleteAccount_WhenFirstDeletion_SetsDeletedAtAndScheduledDate() {
            // Arrange
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

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

            // Auth is token-only, so deletion must push token invalidation itself.
            // The outcome form — a request carrying the old token is rejected — is only
            // reachable through the filter chain, so it needs a MockMvc-tier test.
            verify(tokenBlacklistService).invalidateUserTokens(testUser.getId());
        }

        @Test
        @DisplayName("Should be idempotent and return existing scheduled date")
        void deleteAccount_WhenIdempotent_ReturnsExistingScheduledDate() {
            // Arrange - user already deleted
            Instant existingScheduledAt = Instant.now().plus(Duration.ofDays(25));
            testUser.softDelete(existingScheduledAt);

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            Instant scheduledDate = lifecycleService.deleteAccount(testUser.getId());

            // Assert
            assertEquals(existingScheduledAt, scheduledDate);
            verify(tokenBlacklistService, never()).invalidateUserTokens(any()); // No re-invalidation
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void deleteAccount_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> lifecycleService.deleteAccount(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
        }
    }

    @Nested
    @DisplayName("reactivateAccount Tests")
    class ReactivateAccountTests {

        @Test
        @DisplayName("Should clear deletedAt and scheduledDate on reactivation")
        void reactivateAccount_WhenSoftDeleted_ClearsDeletedAtAndScheduledDate() {
            // Arrange - user is soft-deleted
            testUser.softDelete(Instant.now().plus(Duration.ofDays(30)));
            assertTrue(testUser.isDeleted());

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            assertFalse(testUser.isDeleted());
            assertNull(testUser.getDeletedAt());
            assertNull(testUser.getPermanentDeleteScheduledAt());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void reactivateAccount_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> lifecycleService.reactivateAccount(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
        }

        @Test
        @DisplayName("Should be idempotent for non-deleted user")
        void reactivateAccount_WhenNonDeletedUser_NoOp() {
            // Arrange - user is not deleted
            assertFalse(testUser.isDeleted());

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            assertFalse(testUser.isDeleted());
        }
    }

    @Nested
    @DisplayName("performHardDelete Tests")
    class PerformHardDeleteTests {

        @Test
        @DisplayName("Should anonymize authorship, sweep planner rows, then delete the user")
        void performHardDelete_WhenUserOwnsPlanners_AnonymizesThenSweepsThenDeletes() {
            List<UUID> plannerIds = List.of(UUID.randomUUID());
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.of(testUser));
            when(plannerAccountPurgeService.plannerIdsOwnedBy(testUser.getId())).thenReturn(plannerIds);

            lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            var inOrder = inOrder(plannerAccountPurgeService, commentAccountPurgeService,
                    moderationAccountPurgeService, userRepository);
            inOrder.verify(plannerAccountPurgeService)
                    .reassignVotesToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(commentAccountPurgeService)
                    .reassignAuthorshipToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            // The actor FK is RESTRICT, so the audit rows must change hands before the delete.
            inOrder.verify(moderationAccountPurgeService)
                    .reassignActionsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            // Reports hold no-action FKs to the rows the sweep and the cascade remove, so they go first.
            inOrder.verify(moderationAccountPurgeService).deleteReportsFor(plannerIds);
            inOrder.verify(plannerAccountPurgeService).deleteProjectionsFor(plannerIds);
            // Last: the cascade off this row removes the planner cores the sweep left behind.
            inOrder.verify(userRepository).delete(testUser);
            // A purged account's cookie would otherwise keep authenticating until natural expiry.
            verify(tokenBlacklistService).invalidateUserTokens(testUser.getId());
        }

        @Test
        @DisplayName("Should still anonymize authorship when the account owns no planners")
        void performHardDelete_WhenUserOwnsNoPlanners_SkipsSweepButStillAnonymizes() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.of(testUser));
            when(plannerAccountPurgeService.plannerIdsOwnedBy(testUser.getId())).thenReturn(List.of());

            lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            verify(plannerAccountPurgeService)
                    .reassignVotesToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            verify(commentAccountPurgeService)
                    .reassignAuthorshipToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            verify(moderationAccountPurgeService)
                    .reassignActionsToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            verify(moderationAccountPurgeService, never()).deleteReportsFor(any());
            verify(plannerAccountPurgeService, never()).deleteProjectionsFor(any());
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should skip and touch nothing when the account was reactivated after listing")
        void performHardDelete_WhenNoLongerPurgeable_SkipsWithoutDeleting() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.empty());

            boolean deleted = lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            assertFalse(deleted);
            verify(userRepository, never()).delete(any(User.class));
            verify(tokenBlacklistService, never()).invalidateUserTokens(any());
            verify(plannerAccountPurgeService, never()).reassignVotesToSentinel(any(), any());
            verify(moderationAccountPurgeService, never()).reassignActionsToSentinel(any(), any());
        }
    }
}
