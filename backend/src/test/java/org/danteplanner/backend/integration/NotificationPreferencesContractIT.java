package org.danteplanner.backend.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Tag;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider;
import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider.Preferences;
import org.danteplanner.backend.support.NotificationPreferencesContract;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserSettingsService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class NotificationPreferencesContractIT extends SharedMySqlContainerSupport implements NotificationPreferencesContract {

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public NotificationPreferenceProvider preferences() {
        return userSettingsService;
    }

    @Override
    public User persisted(User user) {
        return userRepository.insert(user);
    }

    @Override
    public void set(Long userId, Preferences userPreferences) {
        userSettingsService.updateSettings(userId, new UpdateUserSettingsRequest(null,
                userPreferences.notifyComments(), userPreferences.notifyRecommendations(),
                userPreferences.notifyNewPublications()));
    }
}
