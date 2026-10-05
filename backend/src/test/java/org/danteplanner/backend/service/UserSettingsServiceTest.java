package org.danteplanner.backend.service;
import org.danteplanner.backend.user.service.UserSettingsService;

import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        testUser = TestDataFactory.unsavedUser(123L);

        existingSettings = UserSettings.builder()
                .user(testUser)
                .syncEnabled(true)
                .syncChoiceMade(true)
                .notifyComments(true)
                .notifyRecommendations(false)
                .notifyNewPublications(false)
                .build();
    }

    @Nested
    class GetSettingsTests {

        @Test
        void getSettings_WhenExistingSettings_ReturnsSettings() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(testUser.getId());

            // Assert
            assertTrue(result.syncEnabled());
            assertTrue(result.syncChoiceMade());
            assertTrue(result.notifyComments());
            assertFalse(result.notifyRecommendations());
            verify(userRepository, never()).findById(any());
        }

        @Test
        void getSettings_WhenNewUser_ReturnsDefaultsWithoutPersisting() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(testUser.getId());

            // Assert
            assertFalse(result.syncEnabled());
            assertFalse(result.syncChoiceMade());
            assertTrue(result.notifyComments());
            assertTrue(result.notifyRecommendations());
            assertFalse(result.notifyNewPublications());
            verify(userSettingsRepository, never()).insert(any());
        }

        @Test
        void getSettings_WhenNonExistentUser_ReturnsDefaults() {
            // Arrange
            Long nonExistentId = 999L;
            when(userSettingsRepository.findByUserId(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(nonExistentId);

            // Assert
            assertFalse(result.syncEnabled());
            assertFalse(result.syncChoiceMade());
            assertTrue(result.notifyComments());
            assertTrue(result.notifyRecommendations());
            assertFalse(result.notifyNewPublications());
        }
    }

    @Nested
    class UpdateSettingsTests {

        @Test
        void updateSettings_WhenOnlySyncEnabled_UpdatesOnlySyncEnabled() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

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
            assertTrue(result.syncChoiceMade());
            assertTrue(result.notifyComments());      // unchanged from existing
            assertFalse(result.notifyRecommendations()); // unchanged from existing
        }

        @Test
        void updateSettings_WhenChoiceNotYetMade_MarksChoiceMade() {
            UserSettings unchosen = UserSettings.builder()
                    .user(testUser)
                    .build();
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(unchosen));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    true, null, null, null
            );

            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            assertTrue(result.syncEnabled());
            assertTrue(result.syncChoiceMade());
        }

        @Test
        void updateSettings_WhenSyncAbsent_LeavesChoiceUnmade() {
            UserSettings unchosen = UserSettings.builder()
                    .user(testUser)
                    .build();
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(unchosen));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    null, false, null, null
            );

            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            assertFalse(result.syncEnabled());
            assertFalse(result.syncChoiceMade());
            assertFalse(result.notifyComments());
        }

        @Test
        void updateSettings_WhenOnlyNotifications_UpdatesOnlyNotifications() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

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
            assertTrue(result.syncChoiceMade());      // unchanged from existing
            assertFalse(result.notifyComments());
            assertTrue(result.notifyRecommendations());
            assertTrue(result.notifyNewPublications());
        }

        @Test
        void updateSettings_WhenAllNulls_NoChanges() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

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
    class GetOrCreateEntityTests {

        @Test
        void getOrCreateEntity_WhenExisting_ReusesEntity() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

            // Act
            UserSettings result = userSettingsService.getOrCreateEntity(testUser.getId());

            // Assert
            assertSame(existingSettings, result);
            verify(userRepository, never()).findById(any());
            verify(userSettingsRepository, never()).insert(any());
        }

        @Test
        void getOrCreateEntity_WhenNotFound_CreatesNew() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));
            when(userSettingsRepository.insert(any(UserSettings.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserSettings result = userSettingsService.getOrCreateEntity(testUser.getId());

            // Assert
            assertNotNull(result);
            assertEquals(testUser, result.getUser());
            verify(userSettingsRepository).insert(any(UserSettings.class));
        }
    }
}
