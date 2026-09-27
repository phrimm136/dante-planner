package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.danteplanner.backend.user.dto.UserResponse;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.RandomUsernameGenerator.UsernameComponents;
import org.danteplanner.backend.user.validation.EpithetValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for user account operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {


    private static final int MAX_USERNAME_RETRIES = 100;

    private static final String USERNAME_SUFFIX_CONSTRAINT = "uk_users_username_suffix";

    private final UserRepository userRepository;
    private final RandomUsernameGenerator usernameGenerator;
    private final EpithetValidator epithetValidator;
    private final ModerationAuditService moderationAuditService;
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
        } catch (DataIntegrityViolationException e) {
            // A declared finder runs in no transaction of its own, so a bare call reaches the
            // primary as undeclared access.
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

    private User createUserWithUniqueUsername(AuthProviderType provider, Map<String, String> userInfo) {
        for (int attempt = 1; attempt <= MAX_USERNAME_RETRIES; attempt++) {
            UsernameComponents username = usernameGenerator.generate();

            User newUser = User.builder()
                    .email(userInfo.get("email"))
                    .provider(provider)
                    .providerId(userInfo.get("id"))
                    .usernameEpithet(username.epithet())
                    .usernameSuffix(username.suffix())
                    .build();

            try {
                return userRepository.insert(newUser);
            } catch (DataIntegrityViolationException e) {
                if (!isUsernameSuffixCollision(e)) {
                    throw e;
                }
                if (attempt % 10 == 0) {
                    log.warn("Username suffix collision after {} attempts, continuing...", attempt);
                }
            }
        }

        log.error("Failed to generate unique username after {} attempts", MAX_USERNAME_RETRIES);
        throw new UsernameGenerationException(MAX_USERNAME_RETRIES);
    }

    /**
     * <p>Spring hands every constraint on the table back as the same exception type, so the key has
     * to be read off the {@link ConstraintViolationException} Hibernate wraps the driver's failure
     * in.</p>
     */
    static boolean isUsernameSuffixCollision(DataIntegrityViolationException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return USERNAME_SUFFIX_CONSTRAINT.equalsIgnoreCase(keyName(violation.getConstraintName()));
            }
        }
        return false;
    }

    /** The key alone: MySQL 8.0.19 and later qualify the key in a duplicate-entry report with its table. */
    private static String keyName(String constraintName) {
        return constraintName == null
                ? null
                : constraintName.substring(constraintName.lastIndexOf('.') + 1);
    }

    public UserResponse toResponse(User user) {
        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .email(user.getEmail())
                .usernameEpithet(user.getUsernameEpithet())
                .usernameSuffix(user.getUsernameSuffix())
                .role(user.getRole().name());

        if (user.isBanned()) {
            builder.isBanned(true)
                    .bannedAt(user.getBannedAt());
            moderationAuditService.latestBanReason(user.getPublicId())
                    .ifPresent(builder::banReason);
        }

        if (user.isTimedOut()) {
            builder.isTimedOut(true)
                    .timeoutUntil(user.getTimeoutUntil());
            moderationAuditService.latestTimeoutReason(user.getPublicId())
                    .ifPresent(builder::timeoutReason);
        }

        return builder.build();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findOptionalById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveById(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveByProvider(AuthProviderType providerType, String providerId) {
        return userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(providerType, providerId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByProvider(AuthProviderType providerType, String providerId) {
        return userRepository.findByProviderAndProviderId(providerType, providerId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveBySuffix(String usernameSuffix) {
        return userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix);
    }

    @Transactional(readOnly = true)
    public List<User> listActiveAccounts() {
        return userRepository.findByDeletedAtIsNullAndIdNot(UserAccountLifecycleService.SENTINEL_USER_ID);
    }

    @Transactional(readOnly = true)
    public List<User> listTimedOutAccounts() {
        return userRepository.findByTimeoutUntilAfterAndDeletedAtIsNull(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<User> findAllByIds(Collection<Long> ids) {
        return userRepository.findAllById(ids);
    }

    /**
     * <p>The lock lives only as long as the transaction that took it, which is the caller's:
     * MANDATORY rejects a call made outside one rather than handing back an unguarded row.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User lockActiveById(Long userId) {
        return userRepository.findWithLockByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public long countByRole(UserRole role) {
        return userRepository.countByRole(role);
    }

    @Transactional
    public User updateUsernameEpithet(Long userId, String epithet) {
        epithetValidator.requireValidEpithet(epithet);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setUsernameEpithet(epithet);

        log.info("User {} updated username epithet to {}", userId, epithet);

        return user;
    }
}
