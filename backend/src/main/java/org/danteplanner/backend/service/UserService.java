package org.danteplanner.backend.service;

import org.danteplanner.backend.config.EpithetConfig;
import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.UsernameGenerationException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.RandomUsernameGenerator.UsernameComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /**
     * Maximum retry attempts for username generation.
     * With 28.6M possible suffixes (31^5), collisions are statistically improbable,
     * but we cap retries to prevent infinite loops in edge cases.
     */
    private static final int MAX_USERNAME_RETRIES = 100;

    private final UserRepository userRepository;
    private final RandomUsernameGenerator usernameGenerator;
    private final EpithetConfig epithetConfig;

    public UserService(UserRepository userRepository, RandomUsernameGenerator usernameGenerator,
                       EpithetConfig epithetConfig) {
        this.userRepository = userRepository;
        this.usernameGenerator = usernameGenerator;
        this.epithetConfig = epithetConfig;
    }

    @Transactional
    public User findOrCreateUser(String provider, Map<String, String> userInfo) {
        String providerId = userInfo.get("id");

        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> createUserWithUniqueUsername(provider, userInfo));
    }

    /**
     * Create a new user with a unique username, retrying on suffix collision.
     * With 28.6M possible suffixes (31^5), collisions are extremely rare.
     *
     * @throws UsernameGenerationException if unable to generate unique username after max retries
     */
    private User createUserWithUniqueUsername(String provider, Map<String, String> userInfo) {
        for (int attempt = 1; attempt <= MAX_USERNAME_RETRIES; attempt++) {
            UsernameComponents username = usernameGenerator.generate();

            User newUser = User.builder()
                    .email(userInfo.get("email"))
                    .provider(provider)
                    .providerId(userInfo.get("id"))
                    .usernameEpithet(username.keyword())
                    .usernameSuffix(username.suffix())
                    .build();

            try {
                return userRepository.save(newUser);
            } catch (DataIntegrityViolationException e) {
                if (attempt % 10 == 0) {
                    log.warn("Username suffix collision after {} attempts, continuing...", attempt);
                }
                // Retry with new suffix
            }
        }

        log.error("Failed to generate unique username after {} attempts", MAX_USERNAME_RETRIES);
        throw new UsernameGenerationException(MAX_USERNAME_RETRIES);
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getPublicId())
                .email(user.getEmail())
                .provider(user.getProvider())
                .usernameEpithet(user.getUsernameEpithet())
                .usernameSuffix(user.getUsernameSuffix())
                .build();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Find an active (non-deleted) user by ID.
     *
     * @param userId the user ID
     * @return the active user, or empty if not found or deleted
     */
    @Transactional(readOnly = true)
    public Optional<User> findActiveById(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId);
    }

    /**
     * Update a user's username epithet.
     * Validates the epithet against the allowed epithets before updating.
     *
     * @param userId  the user ID
     * @param epithet the new epithet (must be a valid epithet)
     * @return the updated user
     * @throws IllegalArgumentException if epithet is not valid
     * @throws UserNotFoundException    if user not found
     */
    @Transactional
    public User updateUsernameEpithet(Long userId, String epithet) {
        if (!epithetConfig.isValidEpithet(epithet)) {
            throw new IllegalArgumentException("Invalid epithet: " + epithet);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setUsernameEpithet(epithet);
        return userRepository.save(user);
    }
}
