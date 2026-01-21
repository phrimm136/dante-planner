package org.danteplanner.backend.scheduler;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserCleanupScheduler.
 *
 * <p>Tests the scheduled job that permanently deletes users
 * whose grace period has expired.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserCleanupSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAccountLifecycleService lifecycleService;

    private UserCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new UserCleanupScheduler(userRepository, lifecycleService);
    }

    private User createExpiredUser(Long id) {
        User user = User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .provider("google")
                .providerId("google-" + id)
                .usernameEpithet("W_CORP")
                .usernameSuffix("tst" + String.format("%02d", id % 100))
                .build();
        user.softDelete(Instant.now().minusSeconds(86400)); // Expired 1 day ago
        return user;
    }

    @Nested
    @DisplayName("cleanupExpiredUsers Tests")
    class CleanupExpiredUsersTests {

        @Test
        @DisplayName("Should delete all expired users")
        void cleanupExpiredUsers_deletesExpiredUsers() {
            // Arrange
            List<User> expiredUsers = List.of(
                    createExpiredUser(1L),
                    createExpiredUser(2L),
                    createExpiredUser(3L)
            );

            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(expiredUsers);
            doNothing().when(lifecycleService).performHardDelete(any(User.class));

            // Act
            scheduler.cleanupExpiredUsers();

            // Assert
            verify(lifecycleService, times(3)).performHardDelete(any(User.class));
            verify(lifecycleService).performHardDelete(expiredUsers.get(0));
            verify(lifecycleService).performHardDelete(expiredUsers.get(1));
            verify(lifecycleService).performHardDelete(expiredUsers.get(2));
        }

        @Test
        @DisplayName("Should do nothing when no expired users exist")
        void cleanupExpiredUsers_noExpiredUsers_doesNothing() {
            // Arrange
            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(new ArrayList<>());

            // Act
            scheduler.cleanupExpiredUsers();

            // Assert
            verify(lifecycleService, never()).performHardDelete(any(User.class));
        }

        @Test
        @DisplayName("Should handle exception gracefully and continue with remaining users")
        void cleanupExpiredUsers_handlesExceptionGracefully() {
            // Arrange
            User user1 = createExpiredUser(1L);
            User user2 = createExpiredUser(2L);
            User user3 = createExpiredUser(3L);
            List<User> expiredUsers = List.of(user1, user2, user3);

            when(userRepository.findByPermanentDeleteScheduledAtBefore(any(Instant.class)))
                    .thenReturn(expiredUsers);

            // First user fails, second and third succeed
            doThrow(new RuntimeException("Database error"))
                    .doNothing()
                    .doNothing()
                    .when(lifecycleService).performHardDelete(any(User.class));

            // Act - should not throw, should continue processing
            scheduler.cleanupExpiredUsers();

            // Assert - all users were attempted
            verify(lifecycleService, times(3)).performHardDelete(any(User.class));
        }

        @Test
        @DisplayName("Should query with current instant")
        void cleanupExpiredUsers_queriesWithCurrentInstant() {
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
