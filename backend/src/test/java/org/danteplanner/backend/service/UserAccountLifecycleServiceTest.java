package org.danteplanner.backend.service;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.validation.RoleChangeValidator;

import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant;
import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant.Call;
import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant.Event;
import org.danteplanner.backend.support.RecordingSessionEndHandler;
import org.danteplanner.backend.support.RecordingSessionEndHandler.EndAllSessions;
import org.danteplanner.backend.support.ScriptedFailuresCheck;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserAccountLifecycleService.
 *
 * <p>Tests soft-delete, reactivation, and hard-delete functionality.</p>
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class UserAccountLifecycleServiceTest {

    private static final String USER_ROW_DELETED = "userRepository.delete";

    @Mock
    private UserRepository userRepository;

    private final List<Object> journal = new ArrayList<>();

    private final RecordingAccountLifecycleParticipant firstParticipant = new RecordingAccountLifecycleParticipant(journal);

    private final RecordingAccountLifecycleParticipant secondParticipant = new RecordingAccountLifecycleParticipant(journal);

    private final RecordingSessionEndHandler sessionEndHandler = new RecordingSessionEndHandler(journal);

    private UserAccountLifecycleService lifecycleService;

    private static final int GRACE_PERIOD_DAYS = 30;

    private User testUser;

    @BeforeEach
    void setUp() {
        lifecycleService = new UserAccountLifecycleService(
                userRepository,
                List.of(firstParticipant, secondParticipant),
                sessionEndHandler,
                new RoleChangeValidator(userRepository), Clock.systemUTC(),
                GRACE_PERIOD_DAYS
        );

        testUser = TestDataFactory.unsavedUser(123L);
    }

    private void journalUserRowDeletion() {
        doAnswer(invocation -> journal.add(USER_ROW_DELETED)).when(userRepository).delete(testUser);
    }

    @Nested
    class DeleteAccountTests {

        @Test
        void deleteAccount_WhenFirstDeletion_SetsDeletedAtAndScheduledDate() {
            // Arrange
            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.softDelete(eq(testUser.getId()), any(), any())).thenReturn(1);

            // Act
            Instant scheduledDate = lifecycleService.deleteAccount(testUser.getId());

            // Assert
            assertThat(scheduledDate).isNotNull();
            verify(userRepository).softDelete(eq(testUser.getId()), any(Instant.class), eq(scheduledDate));

            // Verify scheduled date is approximately 30 days from now
            Instant expectedScheduleMin = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS - 1));
            Instant expectedScheduleMax = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS + 1));
            assertThat(scheduledDate).isAfter(expectedScheduleMin);
            assertThat(scheduledDate).isBefore(expectedScheduleMax);

            assertThat(journal).containsExactly(
                    new Call(firstParticipant, Event.DEACTIVATED, testUser.getId(), null),
                    new Call(secondParticipant, Event.DEACTIVATED, testUser.getId(), null),
                    new EndAllSessions(testUser.getId()));
        }

        @Test
        void deleteAccount_WhenIdempotent_ReturnsExistingScheduledDate() {
            // Arrange - user already deleted
            Instant existingScheduledAt = Instant.now().plus(Duration.ofDays(25));
            TestDataFactory.softDelete(testUser, Instant.now(), existingScheduledAt);

            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            Instant scheduledDate = lifecycleService.deleteAccount(testUser.getId());

            // Assert
            assertThat(scheduledDate).isEqualTo(existingScheduledAt);
            assertThat(journal).isEmpty();
        }

        @Test
        void deleteAccount_WhenTheLastActiveAdminWithdraws_ThrowsLastAdministrator() {
            User admin = TestDataFactory.unsavedUser(UserRole.ADMIN);
            when(userRepository.findWithLockById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(1L);

            assertRejected(() -> lifecycleService.deleteAccount(admin.getId())).withCode("LAST_ADMINISTRATOR");

            verify(userRepository, never()).softDelete(any(), any(), any());
            assertThat(journal).isEmpty();
        }

        @Test
        void deleteAccount_WhenAnotherActiveAdminRemains_SoftDeletesTheAdmin() {
            User admin = TestDataFactory.unsavedUser(UserRole.ADMIN);
            when(userRepository.findWithLockById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(2L);
            when(userRepository.softDelete(eq(admin.getId()), any(), any())).thenReturn(1);

            lifecycleService.deleteAccount(admin.getId());

            verify(userRepository).softDelete(eq(admin.getId()), any(), any());
            assertThat(journal).containsExactly(
                    new Call(firstParticipant, Event.DEACTIVATED, admin.getId(), null),
                    new Call(secondParticipant, Event.DEACTIVATED, admin.getId(), null),
                    new EndAllSessions(admin.getId()));
        }

        @Test
        void deleteAccount_WhenAWithdrawnAdminWithdrawsAgain_ReturnsBeforeTheGuard() {
            User admin = TestDataFactory.unsavedUser(UserRole.ADMIN);
            Instant existingScheduledAt = Instant.now().plus(Duration.ofDays(25));
            TestDataFactory.softDelete(admin, Instant.now(), existingScheduledAt);
            when(userRepository.findWithLockById(admin.getId())).thenReturn(Optional.of(admin));

            assertThat(lifecycleService.deleteAccount(admin.getId())).isEqualTo(existingScheduledAt);
            assertThat(journal).isEmpty();
        }

        @Test
        void deleteAccount_WhenEndingSessionsFails_Propagates() {
            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.softDelete(eq(testUser.getId()), any(), any())).thenReturn(1);
            RedisConnectionFailureException unreachable = new RedisConnectionFailureException("token store unreachable");
            sessionEndHandler.failNext(RecordingSessionEndHandler.Operation.END_ALL_SESSIONS, unreachable);

            assertThatThrownBy(() -> lifecycleService.deleteAccount(testUser.getId()))
                    .isInstanceOf(RedisConnectionFailureException.class)
                    .hasMessage("token store unreachable");
            assertThat(sessionEndHandler.calls()).containsExactly(new RecordingSessionEndHandler.Failed(
                    RecordingSessionEndHandler.Operation.END_ALL_SESSIONS, unreachable));
        }

        @Test
        void deleteAccount_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findWithLockById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> lifecycleService.deleteAccount(nonExistentId))
                    .withCode("USER_NOT_FOUND")
                    .isInstanceOfSatisfying(UserNotFoundException.class,
                            exception -> assertThat(exception.getMessage())
                                    .isEqualTo("User not found with id: " + nonExistentId));
        }
    }

    @Nested
    class ReactivateAccountTests {

        @Test
        void reactivateAccount_WhenSoftDeleted_ClearsDeletedAtAndScheduledDate() {
            // Arrange - user is soft-deleted
            TestDataFactory.softDelete(testUser, Instant.now(), Instant.now().plus(Duration.ofDays(30)));
            assertThat(testUser.isDeleted()).isTrue();

            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.reactivate(eq(testUser.getId()), any())).thenReturn(1);

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            verify(userRepository).reactivate(eq(testUser.getId()), any(Instant.class));
            assertThat(firstParticipant.calls())
                    .containsExactly(new Call(firstParticipant, Event.REACTIVATED, testUser.getId(), null));
            assertThat(secondParticipant.calls())
                    .containsExactly(new Call(secondParticipant, Event.REACTIVATED, testUser.getId(), null));
        }

        @Test
        void reactivateAccount_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findWithLockById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> lifecycleService.reactivateAccount(nonExistentId))
                    .withCode("USER_NOT_FOUND")
                    .isInstanceOfSatisfying(UserNotFoundException.class,
                            exception -> assertThat(exception.getMessage())
                                    .isEqualTo("User not found with id: " + nonExistentId));
        }

        @Test
        void reactivateAccount_WhenNonDeletedUser_NoOp() {
            // Arrange - user is not deleted
            assertThat(testUser.isDeleted()).isFalse();

            when(userRepository.findWithLockById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            lifecycleService.reactivateAccount(testUser.getId());

            // Assert
            verify(userRepository, never()).reactivate(any(), any());
            assertThat(journal).isEmpty();
        }
    }

    @Nested
    class PerformHardDeleteTests {

        @Test
        void performHardDelete_WhenPurgeable_ReassignsEverywhereBeforeAnyDeletion() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.of(testUser));
            journalUserRowDeletion();

            lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            Long sentinel = UserAccountLifecycleService.SENTINEL_USER_ID;
            assertThat(journal).containsExactly(
                    new EndAllSessions(testUser.getId()),
                    new Call(firstParticipant, Event.REASSIGNED_TO_SENTINEL, testUser.getId(), sentinel),
                    new Call(secondParticipant, Event.REASSIGNED_TO_SENTINEL, testUser.getId(), sentinel),
                    new Call(firstParticipant, Event.OWNED_DATA_DELETED, testUser.getId(), null),
                    new Call(secondParticipant, Event.OWNED_DATA_DELETED, testUser.getId(), null),
                    // Last: the cascade off this row removes the planner cores the sweep left behind.
                    USER_ROW_DELETED);
        }

        @Test
        void performHardDelete_WhenEndingSessionsFails_StillPurges() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.of(testUser));
            RedisConnectionFailureException unreachable = new RedisConnectionFailureException("token store unreachable");
            sessionEndHandler.failNext(RecordingSessionEndHandler.Operation.END_ALL_SESSIONS, unreachable);
            journalUserRowDeletion();

            assertThat(lifecycleService.performHardDelete(testUser.getId(), Instant.now())).isTrue();

            Long sentinel = UserAccountLifecycleService.SENTINEL_USER_ID;
            assertThat(sessionEndHandler.calls()).containsExactly(new RecordingSessionEndHandler.Failed(
                    RecordingSessionEndHandler.Operation.END_ALL_SESSIONS, unreachable));
            assertThat(firstParticipant.calls()).containsExactly(
                    new Call(firstParticipant, Event.REASSIGNED_TO_SENTINEL, testUser.getId(), sentinel),
                    new Call(firstParticipant, Event.OWNED_DATA_DELETED, testUser.getId(), null));
            assertThat(journal).last().isEqualTo(USER_ROW_DELETED);
        }

        @Test
        void performHardDelete_WhenNoLongerPurgeable_SkipsWithoutDeleting() {
            when(userRepository.findWithLockPurgeable(eq(testUser.getId()), any(Instant.class)))
                    .thenReturn(Optional.empty());

            boolean deleted = lifecycleService.performHardDelete(testUser.getId(), Instant.now());

            assertThat(deleted).isFalse();
            verify(userRepository, never()).delete(any(User.class));
            assertThat(journal).isEmpty();
        }
    }
}
