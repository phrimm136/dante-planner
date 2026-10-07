package org.danteplanner.backend.service;
import org.danteplanner.backend.user.service.RandomUsernameGenerator;
import org.danteplanner.backend.user.service.UserService;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.user.validation.EpithetValidator;
import org.danteplanner.backend.support.IntegrityViolations;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.support.TransactionCallback;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserService.
 *
 * <p>Tests user lookup and registration functionality.
 * Lifecycle tests (delete, reactivate) are in UserAccountLifecycleServiceTest.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        EpithetConfig epithetConfig = new EpithetConfig();
        userService = new UserService(userRepository, new RandomUsernameGenerator(epithetConfig),
                new EpithetValidator(epithetConfig),
                null, null, transactionTemplate);

        testUser = TestDataFactory.unsavedUser(123L);
    }

    @Nested
    class FindOrCreateUserTests {

        private static final String PROVIDER_ID = "google-123";
        private static final Map<String, String> IDENTITY =
                Map.of("id", PROVIDER_ID, "email", "test@example.com");

        private void runTransactionsInline() {
            when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                    invocation.<TransactionCallback<User>>getArgument(0).doInTransaction(null));
        }

        @Test
        void findOrCreateUser_WhenProviderIdRaceLost_RecoversWithoutRetrying() {
            runTransactionsInline();
            when(userRepository.findByProviderAndProviderId(AuthProviderType.GOOGLE, PROVIDER_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(testUser));
            when(userRepository.insert(any(User.class))).thenThrow(
                    IntegrityViolations.duplicateEntry("users.uk_provider_provider_id"));

            User resolved = userService.findOrCreateUser("google", IDENTITY);

            assertEquals(testUser.getId(), resolved.getId());
            // A constraint no new suffix can satisfy must not be retried against new suffixes.
            verify(userRepository, times(1)).insert(any(User.class));
        }

        @Test
        void findOrCreateUser_WhenSuffixesExhausted_ThrowsUsernameGenerationException() {
            runTransactionsInline();
            when(userRepository.findByProviderAndProviderId(AuthProviderType.GOOGLE, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            when(userRepository.insert(any(User.class))).thenThrow(
                    IntegrityViolations.duplicateEntry("users.uk_users_username_suffix"));

            assertThatThrownBy(() -> userService.findOrCreateUser("google", IDENTITY))
                    .isInstanceOf(UsernameGenerationException.class)
                    .hasMessageStartingWith("Failed to generate unique username");

            // No recovery lookup: exhaustion is not a race, so there is no winner to find.
            verify(userRepository, times(1))
                    .findByProviderAndProviderId(AuthProviderType.GOOGLE, PROVIDER_ID);
        }
    }

    @Nested
    class FindActiveByIdTests {

        @Test
        void findActiveById_WhenActiveUserExists_ReturnsUser() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            // Act
            Optional<User> result = userService.findActiveById(testUser.getId());

            // Assert
            assertTrue(result.isPresent());
            assertEquals(testUser, result.get());
        }

        @Test
        void findActiveById_WhenDeletedUser_ReturnsEmpty() {
            // Arrange - user is deleted, repository returns empty
            when(userRepository.findByIdAndDeletedAtIsNull(testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act
            Optional<User> result = userService.findActiveById(testUser.getId());

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void findActiveById_WhenNonExistentUser_ReturnsEmpty() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findByIdAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act
            Optional<User> result = userService.findActiveById(nonExistentId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FindByIdTests {

        @Test
        void findById_WhenFound_ReturnsUser() {
            // Arrange
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            User result = userService.findById(testUser.getId());

            // Assert
            assertEquals(testUser, result);
        }

        @Test
        void findById_WhenNotFound_ThrowsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> userService.findById(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
        }
    }
}
