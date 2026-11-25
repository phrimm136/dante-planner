package org.danteplanner.backend.user.service;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.validation.WrittenAccount;
import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user notification and preference settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService implements NotificationPreferenceProvider {


    private static final boolean DEFAULT_SYNC_ENABLED = false;
    private static final boolean DEFAULT_SYNC_CHOICE_MADE = false;
    private static final boolean DEFAULT_NOTIFY_COMMENTS = true;
    private static final boolean DEFAULT_NOTIFY_RECOMMENDATIONS = true;
    private static final boolean DEFAULT_NOTIFY_NEW_PUBLICATIONS = false;

    private final UserSettingsRepository userSettingsRepository;

    @Transactional(readOnly = true)
    public ScopedRead<UserSettingsResponse> getSettings(Long userId) {
        UserSettingsResponse settings = userSettingsRepository.findByUserId(userId)
                .map(UserSettingsResponse::fromEntity)
                .orElseGet(() -> new UserSettingsResponse(
                        DEFAULT_SYNC_ENABLED,
                        DEFAULT_SYNC_CHOICE_MADE,
                        DEFAULT_NOTIFY_COMMENTS,
                        DEFAULT_NOTIFY_RECOMMENDATIONS,
                        DEFAULT_NOTIFY_NEW_PUBLICATIONS));
        return new ScopedRead<>(settings, Set.of(new FreshnessScope.User(userId)));
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSettings settings = getOrCreateEntity(userId);

        WrittenAccount written = WrittenAccount.of(userId,
                WriteOutcome.of(userSettingsRepository.update(userId, request)));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()));

        log.debug("Updated settings for user {}", userId);

        return UserSettingsResponse.afterUpdate(settings, request);
    }

    @Override
    @Transactional
    public Preferences preferencesOf(Long userId) {
        UserSettings settings = getOrCreateEntity(userId);
        return new Preferences(
                settings.isNotifyComments(),
                settings.isNotifyRecommendations(),
                settings.isNotifyNewPublications());
    }

    @Transactional
    public UserSettings getOrCreateEntity(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
    }

    private UserSettings createDefaultSettings(Long userId) {
        WrittenAccount written = WrittenAccount.of(userId,
                WriteOutcome.of(userSettingsRepository.insertDefaults(userId)));
        log.info("Created default settings for user {}", written.userId());
        return userSettingsRepository.findWithLockByUserId(written.userId())
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
