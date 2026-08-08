package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.user.dto.UserResponse;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.RandomUsernameGenerator.UsernameComponents;
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
 * Handles OAuth-based user lookup and creation, unique username generation,
 * profile retrieval, and epithet updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {


    /**
     * Maximum retry attempts for username generation.
     * With 28.6M possible suffixes (31^5), collisions are statistically improbable,
     * but we cap retries to prevent infinite loops in edge cases.
     */
    private static final int MAX_USERNAME_RETRIES = 100;

    /** The unique key on {@code users.username_suffix}, as the schema names it. */
    private static final String USERNAME_SUFFIX_CONSTRAINT = "uk_users_username_suffix";

    private final UserRepository userRepository;
    private final RandomUsernameGenerator usernameGenerator;
    private final EpithetConfig epithetConfig;
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
            // Lost the create race on uk_provider_provider_id. The winner committed on the primary,
            // so the recovery re-lookup must run read-write to route there — a bare finder is
            // readOnly and would hit a replica that may not have caught up yet.
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
     * <p>Only a suffix collision is retried. Any other integrity violation — a lost create race on
     * the provider id above all — propagates, because no new suffix can satisfy the constraint that
     * rejected the insert.</p>
     *
     * @throws UsernameGenerationException  if unable to generate unique username after max retries
     * @throws DataIntegrityViolationException if any other constraint rejects the insert
     */
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
     * Whether an integrity violation is the username-suffix constraint rather than another key.
     *
     * <p>Spring hands every constraint on the table back as the same exception type, so the key has
     * to be read off the {@link ConstraintViolationException} Hibernate wraps the driver's failure
     * in. A violation reaching here under any other shape names no key, and is not a collision this
     * method claims.</p>
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

    /** Resolves an id to an account, deleted ones included; empty when none carries the id. */
    @Transactional(readOnly = true)
    public Optional<User> findOptionalById(Long id) {
        return userRepository.findById(id);
    }

    /** Whether an account row carries the given id, deleted or not. */
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    /** Finds a non-deleted account by id; empty when it is missing or deleted. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveById(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId);
    }

    /** Finds the non-deleted account an OAuth identity resolves to. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveByProvider(AuthProviderType providerType, String providerId) {
        return userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(providerType, providerId);
    }

    /**
     * Finds the account an OAuth identity resolves to, soft-deleted ones included, so a returning
     * user can be offered reactivation rather than a second account.
     */
    @Transactional(readOnly = true)
    public Optional<User> findByProvider(AuthProviderType providerType, String providerId) {
        return userRepository.findByProviderAndProviderId(providerType, providerId);
    }

    /** Finds the non-deleted account behind a username suffix, the handle the API exposes. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveBySuffix(String usernameSuffix) {
        return userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix);
    }

    /**
     * Lists the accounts a moderator may act on: every active one except the sentinel that owns
     * anonymized content, which is not a person and cannot be restricted.
     */
    @Transactional(readOnly = true)
    public List<User> listActiveAccounts() {
        return userRepository.findByDeletedAtIsNullAndIdNot(UserAccountLifecycleService.SENTINEL_USER_ID);
    }

    /** Lists the accounts whose timeout has not yet expired. */
    @Transactional(readOnly = true)
    public List<User> listTimedOutAccounts() {
        return userRepository.findByTimeoutUntilAfterAndDeletedAtIsNull(Instant.now());
    }

    /**
     * Resolves a batch of ids in one query. Deleted accounts are included: an audit trail still
     * has to name the actor who left it.
     */
    @Transactional(readOnly = true)
    public List<User> findAllByIds(Collection<Long> ids) {
        return userRepository.findAllById(ids);
    }

    /**
     * Read an active account under a write lock, so a rank check and the write it guards cannot be
     * interleaved by a concurrent role change.
     *
     * <p>The lock lives only as long as the transaction that took it, which is the caller's:
     * MANDATORY rejects a call made outside one rather than handing back an unguarded row.</p>
     *
     * @param userId the account id
     * @return the locked active account
     * @throws UserNotFoundException if no active account carries the id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User lockActiveById(Long userId) {
        return userRepository.findWithLockByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /** Counts the accounts holding a role, deleted ones included. */
    @Transactional(readOnly = true)
    public long countByRole(UserRole role) {
        return userRepository.countByRole(role);
    }

    /**
     * Update a user's username epithet.
     * Validates the epithet against the allowed epithets before updating.
     *
     * @param userId  the user ID
     * @param epithet the new epithet (must be a valid epithet)
     * @return the updated user
     * @throws InvalidRequestException if epithet is not valid
     * @throws UserNotFoundException    if user not found
     */
    @Transactional
    public User updateUsernameEpithet(Long userId, String epithet) {
        if (!epithetConfig.isValidEpithet(epithet)) {
            throw new InvalidRequestException("INVALID_EPITHET", "Invalid epithet: " + epithet);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setUsernameEpithet(epithet);

        log.info("User {} updated username epithet to {}", userId, epithet);

        return user;
    }
}
