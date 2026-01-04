package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    private RandomUsernameGenerator usernameGenerator;

    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, usernameGenerator);

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();
    }

    @Nested
    @DisplayName("findActiveById Tests")
    class FindActiveByIdTests {

        @Test
        @DisplayName("Should return active user when found")
        void findActiveById_returnsActiveUser() {
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
        @DisplayName("Should return empty for deleted user")
        void findActiveById_deletedUser_returnsEmpty() {
            // Arrange - user is deleted, repository returns empty
            when(userRepository.findByIdAndDeletedAtIsNull(testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act
            Optional<User> result = userService.findActiveById(testUser.getId());

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for non-existent user")
        void findActiveById_nonExistentUser_returnsEmpty() {
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
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return user when found")
        void findById_found_returnsUser() {
            // Arrange
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            // Act
            User result = userService.findById(testUser.getId());

            // Assert
            assertEquals(testUser, result);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when not found")
        void findById_notFound_throwsException() {
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
