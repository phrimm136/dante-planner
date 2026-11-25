package org.danteplanner.backend.service;
import org.danteplanner.backend.user.service.UserSettingsService;

import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
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

    private UserSettingsService userSettingsService;

    private User testUser;
    private UserSettings existingSettings;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(userSettingsRepository);

        testUser = TestDataFactory.unsavedUser(123L);

        existingSettings = UserSettings.builder()
                .userId(testUser.getId())
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
            UserSettingsResponse result = userSettingsService.getSettings(testUser.getId()).value();

            // Assert
            assertThat(result.syncEnabled()).isTrue();
            assertThat(result.syncChoiceMade()).isTrue();
            assertThat(result.notifyComments()).isTrue();
            assertThat(result.notifyRecommendations()).isFalse();
        }

        @Test
        void getSettings_WhenNewUser_ReturnsDefaultsWithoutPersisting() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(testUser.getId()).value();

            // Assert
            assertThat(result.syncEnabled()).isFalse();
            assertThat(result.syncChoiceMade()).isFalse();
            assertThat(result.notifyComments()).isTrue();
            assertThat(result.notifyRecommendations()).isTrue();
            assertThat(result.notifyNewPublications()).isFalse();
            verify(userSettingsRepository, never()).insertDefaults(any());
        }

        @Test
        void getSettings_WhenRead_ScopesTheUser() {
            when(userSettingsRepository.findByUserId(7L)).thenReturn(Optional.empty());

            assertThat(userSettingsService.getSettings(7L).scopes()).containsExactly(new FreshnessScope.User(7L));
        }

        @Test
        void getSettings_WhenNonExistentUser_ReturnsDefaults() {
            // Arrange
            Long nonExistentId = 999L;
            when(userSettingsRepository.findByUserId(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act
            UserSettingsResponse result = userSettingsService.getSettings(nonExistentId).value();

            // Assert
            assertThat(result.syncEnabled()).isFalse();
            assertThat(result.syncChoiceMade()).isFalse();
            assertThat(result.notifyComments()).isTrue();
            assertThat(result.notifyRecommendations()).isTrue();
            assertThat(result.notifyNewPublications()).isFalse();
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

            when(userSettingsRepository.update(testUser.getId(), request)).thenReturn(1);

            // Act
            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            // Assert
            assertThat(result.syncEnabled()).isFalse();
            assertThat(result.syncChoiceMade()).isTrue();
            assertThat(result.notifyComments()).isTrue();      // unchanged from existing
            assertThat(result.notifyRecommendations()).isFalse(); // unchanged from existing
        }

        @Test
        void updateSettings_WhenChoiceNotYetMade_MarksChoiceMade() {
            UserSettings unchosen = UserSettings.builder()
                .userId(testUser.getId())
                    .build();
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(unchosen));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    true, null, null, null
            );
            when(userSettingsRepository.update(testUser.getId(), request)).thenReturn(1);

            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            assertThat(result.syncEnabled()).isTrue();
            assertThat(result.syncChoiceMade()).isTrue();
        }

        @Test
        void updateSettings_WhenSyncAbsent_LeavesChoiceUnmade() {
            UserSettings unchosen = UserSettings.builder()
                .userId(testUser.getId())
                    .build();
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(unchosen));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    null, false, null, null
            );
            when(userSettingsRepository.update(testUser.getId(), request)).thenReturn(1);

            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            assertThat(result.syncEnabled()).isFalse();
            assertThat(result.syncChoiceMade()).isFalse();
            assertThat(result.notifyComments()).isFalse();
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

            when(userSettingsRepository.update(testUser.getId(), request)).thenReturn(1);

            // Act
            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            // Assert
            assertThat(result.syncEnabled()).isTrue();         // unchanged from existing
            assertThat(result.syncChoiceMade()).isTrue();      // unchanged from existing
            assertThat(result.notifyComments()).isFalse();
            assertThat(result.notifyRecommendations()).isTrue();
            assertThat(result.notifyNewPublications()).isTrue();
        }

        @Test
        void updateSettings_WhenAllNulls_NoChanges() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

            UpdateUserSettingsRequest request = new UpdateUserSettingsRequest(
                    null, null, null, null
            );

            when(userSettingsRepository.update(testUser.getId(), request)).thenReturn(1);

            // Act
            UserSettingsResponse result = userSettingsService.updateSettings(testUser.getId(), request);

            // Assert - all values unchanged
            assertThat(result.syncEnabled()).isTrue();
            assertThat(result.notifyComments()).isTrue();
            assertThat(result.notifyRecommendations()).isFalse();
            assertThat(result.notifyNewPublications()).isFalse();
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
            assertThat(result).isSameAs(existingSettings);
            verify(userSettingsRepository, never()).insertDefaults(any());
        }

        @Test
        void getOrCreateEntity_WhenNotFound_CreatesNew() {
            // Arrange
            when(userSettingsRepository.findByUserId(testUser.getId()))
                    .thenReturn(Optional.empty());
            when(userSettingsRepository.insertDefaults(testUser.getId())).thenReturn(1);
            when(userSettingsRepository.findWithLockByUserId(testUser.getId()))
                    .thenReturn(Optional.of(existingSettings));

            // Act
            UserSettings result = userSettingsService.getOrCreateEntity(testUser.getId());

            // Assert
            assertThat(result).isSameAs(existingSettings);
        }

        @Test
        void getOrCreateEntity_WhenTheUserDoesNotExist_AnswersNotFound() {
            when(userSettingsRepository.findByUserId(999L)).thenReturn(Optional.empty());
            when(userSettingsRepository.insertDefaults(999L)).thenReturn(0);

            assertRejected(() -> userSettingsService.getOrCreateEntity(999L)).withCode("USER_NOT_FOUND");
        }
    }
}
