package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.user.UpdateUserSettingsRequest;
import org.danteplanner.backend.dto.user.UserSettingsResponse;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserSettings;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserSettingsService.
 *
 * <p>Tests lazy creation, partial update, and getOrCreateEntity functionality.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private UserRepository userRepository;

    private UserSettingsService userSettingsService;

    private User testUser;
    private UserSettings existingSettings;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(userSettingsRepository, userRepository);

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();

        existingSettings = UserSettings.builder()
                .user(testUser)
                .syncEnabled(true)
                .notifyComments(true)
                .notifyRecommendations(false)
                .notifyNewPublications(false)
                .build();
    }

    @Nested
    @DisplayName("getSettings Tests - Lazy Creation")
    class GetSettingsTests {

        @Test
        @DisplayName("Should return existing settings when found")
        void getSettings_existingSettings_returnsSettings() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(testUser.getId());

            // Assert
            assertTrue(result.syncEnabled());
            assertTrue(result.notifyComments());
            assertFalse(result.notifyRecommendations());
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should create default settings for new user (lazy creation)")
        void getSettings_newUser_createsDefaultSettings() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            UserSettings savedSettings = UserSettings.builder()
                    .user(testUser)
                    .syncEnabled(null)
                    .notifyComments(true)
                    .notifyRecommendations(true)
                    .notifyNewPublications(false)
                    .build();
            when(userSettingsRepository.save(any(UserSettings.class)))
                    .thenReturn(savedSettings);

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(testUser.getId());

            // Assert
            assertNull(result.syncEnabled());
            assertTrue(result.notifyComments());
            assertTrue(result.notifyRecommendations());
            assertFalse(result.notifyNewPublications());

            // Verify save was called with default values
            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(captor.capture());
            UserSettings captured = captor.getValue();
            assertNull(captured.getSyncEnabled());
            assertTrue(captured.isNotifyComments());
            assertTrue(captured.isNotifyRecommendations());
            assertFalse(captured.isNotifyNewPublications());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException for non-existent user")
        void getSettings_nonExistentUser_throwsException() {
            // Arrange
            Long nonExistentId = 999L;
            when(userSettingsRepository.findByUserId(nonExistentId))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> userSettingsService.getSettings(nonExistentId)
            );

            assertEquals(nonExistentId, exception.getUserId());
        }
    }

    @Nested
    @DisplayName("updateSettings Tests - Partial Update")
    class UpdateSettingsTests {

        @Test
        @DisplayName("Should update only syncEnabled when only syncEnabled provided")
        void updateSettings_onlySyncEnabled_updatesOnlySyncEnabled() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));
            when(userSettingsRepository.saveAndFlush(any(UserSettings.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    false,  // syncEnabled
                    null,   // notifyComments - should not change
                    null,   // notifyRecommendations - should not change
                    null    // notifyNewPublications - should not change
            );

            // Act
            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            // Assert
            assertFalse(result.syncEnabled());
            assertTrue(result.notifyComments());      // unchanged from existing
            assertFalse(result.notifyRecommendations()); // unchanged from existing
        }

        @Test
        @DisplayName("Should update only notification settings when sync not provided")
        void updateSettings_onlyNotifications_updatesOnlyNotifications() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));
            when(userSettingsRepository.saveAndFlush(any(UserSettings.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    null,   // syncEnabled - should not change
                    false,  // notifyComments
                    true,   // notifyRecommendations
                    true    // notifyNewPublications
            );

            // Act
            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            // Assert
            assertTrue(result.syncEnabled());         // unchanged from existing
            assertFalse(result.notifyComments());
            assertTrue(result.notifyRecommendations());
            assertTrue(result.notifyNewPublications());
        }

        @Test
        @DisplayName("Should not modify any field when all nulls provided")
        void updateSettings_allNulls_noChanges() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));
            when(userSettingsRepository.saveAndFlush(any(UserSettings.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    null, null, null, null
            );

            // Act
            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            // Assert - all values unchanged
            assertTrue(result.syncEnabled());
            assertTrue(result.notifyComments());
            assertFalse(result.notifyRecommendations());
            assertFalse(result.notifyNewPublications());
        }
    }

    @Nested
    @DisplayName("getOrCreateEntity Tests")
    class GetOrCreateEntityTests {

        @Test
        @DisplayName("Should reuse existing entity")
        void getOrCreateEntity_existing_reusesEntity() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

            // Act
            UserSettings result = userSettingsService.getOrCreateEntity(testUser.getId());

            // Assert
            assertSame(existingSettings, result);
            verify(userRepository, never()).findById(any());
            verify(userSettingsRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create new entity when not found")
        void getOrCreateEntity_notFound_createsNew() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));
            when(userSettingsRepository.save(any(UserSettings.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserSettings result = userSettingsService.getOrCreateEntity(testUser.getId());

            // Assert
            assertNotNull(result);
            assertEquals(testUser, result.getUser());
            verify(userSettingsRepository).save(any(UserSettings.class));
        }
    }
}
