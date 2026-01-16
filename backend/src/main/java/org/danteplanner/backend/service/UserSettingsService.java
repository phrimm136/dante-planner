package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.user.UpdateUserSettingsRequest;
import org.danteplanner.backend.dto.user.UserSettingsResponse;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserSettings;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    public UserSettingsService(UserSettingsRepository userSettingsRepository, UserRepository userRepository) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get user settings with lazy creation.
     * If no settings exist for the user, creates default settings.
     *
     * @param userId the user ID
     * @return the user settings response
     */
    @Transactional
    public UserSettingsResponse getSettings(Long userId) {
        UserSettings settings = getOrCreateEntity(userId);
        return UserSettingsResponse.fromEntity(settings);
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
                .syncEnabled(null)
                .notifyComments(true)
                .notifyRecommendations(true)
                .notifyNewPublications(false)
                .build();

        log.info("Created default settings for user {}", userId);
        return userSettingsRepository.save(settings);
    }
}
