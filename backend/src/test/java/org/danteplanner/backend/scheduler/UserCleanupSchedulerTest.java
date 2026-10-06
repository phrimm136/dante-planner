package org.danteplanner.backend.scheduler;
import org.danteplanner.backend.user.scheduler.UserCleanupScheduler;

import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant;
import org.danteplanner.backend.support.RecordingAccountLifecycleParticipant.Event;
import org.danteplanner.backend.support.RecordingSessionEndHandler;
import org.danteplanner.backend.support.RecordingSessionEndHandler.EndAllSessions;
import org.danteplanner.backend.support.ScriptedFailuresCheck;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserCleanupScheduler.
 *
 * <p>Tests the scheduled job that permanently deletes users
 * whose grace period has expired.</p>
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class UserCleanupSchedulerTest {

    private static final int GRACE_PERIOD_DAYS = 30;

    @Mock
    private UserRepository userRepository;

    private final RecordingAccountLifecycleParticipant participant = new RecordingAccountLifecycleParticipant();

    private final RecordingSessionEndHandler sessionEndHandler = new RecordingSessionEndHandler();

    private UserCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new UserCleanupScheduler(userRepository, new UserAccountLifecycleService(
                userRepository, List.of(participant), sessionEndHandler, GRACE_PERIOD_DAYS));
    }

    private void givenPurgeable(User user) {
        when(userRepository.findWithLockPurgeable(eq(user.getId()), any(Instant.class))).thenReturn(Optional.of(user));
    }

    private List<Long> sessionsEnded() {
        return sessionEndHandler.calls().stream()
                .filter(EndAllSessions.class::isInstance)
                .map(call -> ((EndAllSessions) call).userId())
                .toList();
    }

    private User createExpiredUser(Long id) {
        User user = User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + id)
                .usernameEpithet("W_CORP")
                .usernameSuffix("tst" + String.format("%02d", id % 100))
                .build();
        user.softDelete(Instant.now().minusSeconds(86400)); // Expired 1 day ago
        return user;
    }

    @Nested
    class CleanupExpiredUsersTests {

        @Test
        void cleanupExpiredUsers_WhenExpiredExist_DeletesAll() {
            // Arrange
            List<User> expiredUsers = List.of(
                    createExpiredUser(1L),
                    createExpiredUser(2L),
                    createExpiredUser(3L)
            );

            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(expiredUsers);
            expiredUsers.forEach(UserCleanupSchedulerTest.this::givenPurgeable);

            // Act
            scheduler.cleanupExpiredUsers();

            // Assert
            assertThat(sessionsEnded()).containsExactly(1L, 2L, 3L);
            for (User expired : expiredUsers) {
                verify(userRepository).delete(expired);
            }
        }

        @Test
        void cleanupExpiredUsers_WhenNoExpiredUsers_DoesNothing() {
            // Arrange
            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(new ArrayList<>());

            // Act
            scheduler.cleanupExpiredUsers();

            // Assert
            verify(userRepository, never()).findWithLockPurgeable(any(), any());
        }

        @Test
        void cleanupExpiredUsers_WhenDeleteFails_ContinuesWithRest() {
            // Arrange
            User user1 = createExpiredUser(1L);
            User user2 = createExpiredUser(2L);
            User user3 = createExpiredUser(3L);
            List<User> expiredUsers = List.of(user1, user2, user3);

            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(expiredUsers);
            expiredUsers.forEach(UserCleanupSchedulerTest.this::givenPurgeable);

            // First user fails, second and third succeed
            RuntimeException databaseError = new RuntimeException("Database error");
            participant.failNext(Event.REASSIGNED_TO_SENTINEL, databaseError);

            // Act - should not throw, should continue processing
            scheduler.cleanupExpiredUsers();

            // Assert - all users were attempted
            assertThat(sessionsEnded()).containsExactly(1L, 2L, 3L);
            assertThat(participant.failed()).containsExactly(new RecordingAccountLifecycleParticipant.Failed(
                    participant, Event.REASSIGNED_TO_SENTINEL, 1L, databaseError));
            verify(userRepository, never()).delete(user1);
            verify(userRepository).delete(user2);
            verify(userRepository).delete(user3);
        }

        @Test
        void cleanupExpiredUsers_WhenRun_QueriesWithCurrentInstant() {
            // Arrange
            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(new ArrayList<>());

            Instant beforeCall = Instant.now();

            // Act
            scheduler.cleanupExpiredUsers();

            Instant afterCall = Instant.now();

            // Assert - verify query was called with an instant between before and after
            verify(userRepository).findByPermanentDeleteScheduledAtBefore(
                    argThat(instant -> !instant.isBefore(beforeCall) && !instant.isAfter(afterCall))
            );
        }
    }
}
