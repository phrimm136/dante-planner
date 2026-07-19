package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user notification and preference settings.
 * Handles retrieval and updates with lazy creation of default settings.
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private static final Boolean DEFAULT_SYNC_ENABLED = null;
    private static final boolean DEFAULT_NOTIFY_COMMENTS = true;
    private static final boolean DEFAULT_NOTIFY_RECOMMENDATIONS = true;
    private static final boolean DEFAULT_NOTIFY_NEW_PUBLICATIONS = false;

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    /**
     * Get user settings with lazy creation.
     * If no settings exist for the user, creates default settings.
     *
     * @param userId the user ID
     * @return the user settings response
     */
    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(UserSettingsResponse::fromEntity)
                .orElseGet(() -> new UserSettingsResponse(
                        DEFAULT_SYNC_ENABLED,
                        DEFAULT_NOTIFY_COMMENTS,
                        DEFAULT_NOTIFY_RECOMMENDATIONS,
                        DEFAULT_NOTIFY_NEW_PUBLICATIONS));
    }

    /**
     * Update user settings with partial update semantics.
     * Only non-null fields in the request will be updated.
     *
     * @param userId  the user ID
     * @param request the update request with optional fields
     * @return the updated user settings response
     */
    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSettings settings = getOrCreateEntity(userId);

        if (request.syncEnabled() != null) {
            settings.setSyncEnabled(request.syncEnabled());
        }
        if (request.notifyComments() != null) {
            settings.setNotifyComments(request.notifyComments());
        }
        if (request.notifyRecommendations() != null) {
            settings.setNotifyRecommendations(request.notifyRecommendations());
        }
        if (request.notifyNewPublications() != null) {
            settings.setNotifyNewPublications(request.notifyNewPublications());
        }

        UserSettings saved = userSettingsRepository.save(settings);
        log.debug("Updated settings for user {}", userId);

        return UserSettingsResponse.fromEntity(saved);
    }

    /**
     * Get or create user settings entity.
     * Creates default settings if none exist for the user.
     *
     * @param userId the user ID
     * @return the existing or newly created settings entity
     */
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
                .notifyComments(DEFAULT_NOTIFY_COMMENTS)
                .notifyRecommendations(DEFAULT_NOTIFY_RECOMMENDATIONS)
                .notifyNewPublications(DEFAULT_NOTIFY_NEW_PUBLICATIONS)
                .build();

        log.info("Created default settings for user {}", userId);
        return userSettingsRepository.save(settings);
    }
}
