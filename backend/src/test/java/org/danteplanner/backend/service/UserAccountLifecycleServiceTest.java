package org.danteplanner.backend.service;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.AccountLifecycleParticipant;
import org.danteplanner.backend.user.api.SessionEndHandler;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private AccountLifecycleParticipant firstParticipant;

    @Mock
    private AccountLifecycleParticipant secondParticipant;

    @Mock
    private SessionEndHandler sessionEndHandler;

    private UserAccountLifecycleService lifecycleService;

    private static final int GRACE_PERIOD_DAYS = 30;

    private User testUser;

    @BeforeEach
    void setUp() {
        lifecycleService = new UserAccountLifecycleService(
                userRepository,
                List.of(firstParticipant, secondParticipant),
                sessionEndHandler,
                GRACE_PERIOD_DAYS
        );

        testUser = TestDataFactory.unsavedUser(123L);
    }

    @Nested
    class DeleteAccountTests {

        @Test
        void deleteAccount_WhenFirstDeletion_SetsDeletedAtAndScheduledDate() {
            // Arrange
            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));

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

            var inOrder = inOrder(firstParticipant, secondParticipant, sessionEndHandler);
            inOrder.verify(firstParticipant).accountDeactivated(testUser.getId());
            inOrder.verify(secondParticipant).accountDeactivated(testUser.getId());
            inOrder.verify(sessionEndHandler).endAllSessions(testUser.getId());
        }

        @Test
        void deleteAccount_WhenIdempotent_ReturnsExistingScheduledDate() {
            // Arrange - user already deleted
            Instant existingScheduledAt = Instant.now().plus(Duration.ofDays(25));
            testUser.softDelete(existingScheduledAt);

            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            Instant scheduledDate = lifecycleService.deleteAccount(testUser.getId());

            // Assert
            assertEquals(existingScheduledAt, scheduledDate);
            verify(firstParticipant, never()).accountDeactivated(any());
            verify(sessionEndHandler, never()).endAllSessions(any());
        }

        @Test
        void deleteAccount_WhenEndingSessionsFails_Propagates() {
            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));
            doThrow(new RedisConnectionFailureException("token store unreachable"))
                    .when(sessionEndHandler).endAllSessions(testUser.getId());

            assertThrows(RedisConnectionFailureException.class,
                    () -> lifecycleService.deleteAccount(testUser.getId()));
        }

        @Test
        void deleteAccount_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findWithLockById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> lifecycleService.deleteAccount(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
        }
    }

    @Nested
    class ReactivateAccountTests {

        @Test
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
            verify(firstParticipant).accountReactivated(testUser.getId());
            verify(secondParticipant).accountReactivated(testUser.getId());
        }

        @Test
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
        void reactivateAccount_WhenNonDeletedUser_NoOp() {
            // Arrange - user is not deleted
            assertFalse(testUser.isDeleted());

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            assertFalse(testUser.isDeleted());
            verify(firstParticipant, never()).accountReactivated(any());
        }
    }

    @Nested
    class PerformHardDeleteTests {

        @Test
        void performHardDelete_WhenPurgeable_ReassignsEverywhereBeforeAnyDeletion() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.of(testUser));

            lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            var inOrder = inOrder(sessionEndHandler, firstParticipant, secondParticipant, userRepository);
            inOrder.verify(sessionEndHandler).endAllSessions(testUser.getId());
            inOrder.verify(firstParticipant)
                    .reassignToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(secondParticipant)
                    .reassignToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            inOrder.verify(firstParticipant).deleteOwnedData(testUser.getId());
            inOrder.verify(secondParticipant).deleteOwnedData(testUser.getId());
            // Last: the cascade off this row removes the planner cores the sweep left behind.
            inOrder.verify(userRepository).delete(testUser);
        }

        @Test
        void performHardDelete_WhenEndingSessionsFails_StillPurges() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.of(testUser));
            doThrow(new RedisConnectionFailureException("token store unreachable"))
                    .when(sessionEndHandler).endAllSessions(testUser.getId());

            assertTrue(lifecycleService.performHardDelete(testUser.getId(), Instant.now()));

            verify(firstParticipant)
                    .reassignToSentinel(testUser.getId(), UserAccountLifecycleService.SENTINEL_USER_ID);
            verify(firstParticipant).deleteOwnedData(testUser.getId());
            verify(userRepository).delete(testUser);
        }

        @Test
        void performHardDelete_WhenNoLongerPurgeable_SkipsWithoutDeleting() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.empty());

            boolean deleted = lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            assertFalse(deleted);
            verify(userRepository, never()).delete(any(User.class));
            verifyNoInteractions(firstParticipant, secondParticipant, sessionEndHandler);
        }
    }
}
