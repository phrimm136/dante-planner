package org.danteplanner.backend.user.service;

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user notification and preference settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {


    private static final boolean DEFAULT_SYNC_ENABLED = false;
    private static final boolean DEFAULT_SYNC_CHOICE_MADE = false;
    private static final boolean DEFAULT_NOTIFY_COMMENTS = true;
    private static final boolean DEFAULT_NOTIFY_RECOMMENDATIONS = true;
    private static final boolean DEFAULT_NOTIFY_NEW_PUBLICATIONS = false;

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(UserSettingsResponse::fromEntity)
                .orElseGet(() -> new UserSettingsResponse(
                        DEFAULT_SYNC_ENABLED,
                        DEFAULT_SYNC_CHOICE_MADE,
                        DEFAULT_NOTIFY_COMMENTS,
                        DEFAULT_NOTIFY_RECOMMENDATIONS,
                        DEFAULT_NOTIFY_NEW_PUBLICATIONS));
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSettings settings = getOrCreateEntity(userId);

        applyIfPresent(request.syncEnabled(), settings::chooseSync);
        applyIfPresent(request.notifyComments(), settings::setNotifyComments);
        applyIfPresent(request.notifyRecommendations(), settings::setNotifyRecommendations);
        applyIfPresent(request.notifyNewPublications(), settings::setNotifyNewPublications);

        log.debug("Updated settings for user {}", userId);

        return UserSettingsResponse.fromEntity(settings);
    }

    private static <T> void applyIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    @Transactional
    public UserSettings getOrCreateEntity(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
    }

    private UserSettings createDefaultSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        UserSettings settings = UserSettings.builder()
                .user(user)
                .syncEnabled(DEFAULT_SYNC_ENABLED)
                .syncChoiceMade(DEFAULT_SYNC_CHOICE_MADE)
                .notifyComments(DEFAULT_NOTIFY_COMMENTS)
                .notifyRecommendations(DEFAULT_NOTIFY_RECOMMENDATIONS)
                .notifyNewPublications(DEFAULT_NOTIFY_NEW_PUBLICATIONS)
                .build();

        log.info("Created default settings for user {}", userId);
        return userSettingsRepository.insert(settings);
    }
}
