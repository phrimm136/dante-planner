package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.user.dto.UserDto;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.RandomUsernameGenerator.UsernameComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Service for user account operations.
 * Handles OAuth-based user lookup and creation, unique username generation,
 * profile retrieval, and epithet updates.
 */
@Service
@RequiredArgsConstructor
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
    private final ModerationActionRepository moderationActionRepository;
    private final UserSettingsService userSettingsService;
    private final TransactionTemplate transactionTemplate;

    public User findOrCreateUser(String provider, Map<String, String> userInfo) {
        AuthProviderType providerType = AuthProviderType.fromValue(provider);
        String providerId = userInfo.get("id");

        Optional<User> existing = userRepository.findByProviderAndProviderId(providerType, providerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return transactionTemplate.execute(status -> createOrRecover(providerType, userInfo));
        } catch (DataIntegrityViolationException | UsernameGenerationException e) {
            // Lost the create race on uk_provider_provider_id (the username retry masks the
            // provider-id collision as exhaustion). The winner committed on the primary, so the
            // recovery re-lookup must run read-write to route there — a bare finder is readOnly
            // and would hit a replica that may not have caught up yet.
            return transactionTemplate.execute(status ->
                    userRepository.findByProviderAndProviderId(providerType, providerId)
                            .orElseThrow(() -> e));
        }
    }

    private User createOrRecover(AuthProviderType providerType, Map<String, String> userInfo) {
        User user = createUserWithUniqueUsername(providerType, userInfo);
        userSettingsService.getOrCreateEntity(user.getId());
        return user;
    }

    /**
     * Create a new user with a unique username, retrying on suffix collision.
     * With 28.6M possible suffixes (31^5), collisions are extremely rare.
     *
     * @throws UsernameGenerationException if unable to generate unique username after max retries
     */
    private User createUserWithUniqueUsername(AuthProviderType provider, Map<String, String> userInfo) {
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
        UserDto.UserDtoBuilder builder = UserDto.builder()
                .email(user.getEmail())
                .usernameEpithet(user.getUsernameEpithet())
                .usernameSuffix(user.getUsernameSuffix())
                .role(user.getRole().name());

        // Add ban status if user is banned
        if (user.isBanned()) {
            builder.isBanned(true)
                    .bannedAt(user.getBannedAt());

            // Fetch ban reason from audit trail
            moderationActionRepository.findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(
                            user.getPublicId().toString(), ModerationAction.ActionType.BAN)
                    .ifPresent(action -> builder.banReason(action.getReason()));
        }

        // Add timeout status if user is timed out
        if (user.isTimedOut()) {
            builder.isTimedOut(true)
                    .timeoutUntil(user.getTimeoutUntil());

            // Fetch timeout reason from audit trail
            moderationActionRepository.findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(
                            user.getPublicId().toString(), ModerationAction.ActionType.TIMEOUT)
                    .ifPresent(action -> builder.timeoutReason(action.getReason()));
        }

        return builder.build();
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
