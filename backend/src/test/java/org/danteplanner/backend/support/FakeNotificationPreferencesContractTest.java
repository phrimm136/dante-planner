package org.danteplanner.backend.support;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider;
import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider.Preferences;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.service.UserSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FakeNotificationPreferencesContractTest implements NotificationPreferencesContract {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    private final FakeUsers users = new FakeUsers();
    private final FakeNotificationPreferences preferences = new FakeNotificationPreferences(users);

    @Override
    public NotificationPreferenceProvider preferences() {
        return preferences;
    }

    @Override
    public User persisted(User user) {
        return users.add(user);
    }

    @Override
    public void set(Long userId, Preferences userPreferences) {
        preferences.set(userId, userPreferences);
    }

    @Test
    void preferencesOf_WhenNeitherSideHoldsSettings_AnswersWhatTheRealServiceCreates() {
        User user = persisted(UsersContract.newUser().build());
        when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(userSettingsRepository.insertDefaults(user.getId())).thenReturn(1);
        when(userSettingsRepository.findWithLockByUserId(user.getId()))
                .thenReturn(Optional.of(UserSettings.builder().userId(user.getId()).build()));
        UserSettingsService real = new UserSettingsService(userSettingsRepository);

        assertThat(preferences.preferencesOf(user.getId())).isEqualTo(real.preferencesOf(user.getId()));
    }
}
