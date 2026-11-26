package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.exception.ConstraintViolationClassifier;
import org.hibernate.exception.ConstraintViolationException;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.shared.restriction.Restriction;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.user.exception.UsernameGenerationException;
import org.danteplanner.backend.user.api.UserMiss;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.RestrictionReasonProvider;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.RandomUsernameGenerator.UsernameComponents;
import org.danteplanner.backend.user.validation.EpithetValidator;
import org.danteplanner.backend.user.validation.EpithetValidator.Epithet;
import org.danteplanner.backend.user.validation.RoleChangeValidator.AllowedRoleChange;
import org.danteplanner.backend.user.validation.WrittenAccount;
import org.danteplanner.backend.shared.freshness.FreshnessExempt;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final RestrictionReasonProvider restrictionReasonProvider;
    private final UserSettingsService userSettingsService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public User findOrCreateUser(String provider, String providerId, String email) {
        AuthProviderType providerType = AuthProviderType.fromValue(provider);

        try {
            return transactionTemplate.execute(status ->
                    userRepository.findByProviderAndProviderId(providerType, providerId)
                            .orElseGet(() -> createOrRecover(providerType, providerId, email)));
        } catch (DataIntegrityViolationException e) {
            // A declared finder runs in no transaction of its own, so a bare call reaches the
            // primary as undeclared access.
            return transactionTemplate.execute(status ->
                    userRepository.findByProviderAndProviderId(providerType, providerId)
                            .orElseThrow(() -> e));
        }
    }

    private User createOrRecover(AuthProviderType providerType, String providerId, String email) {
        User user = createUserWithUniqueUsername(providerType, providerId, email);
        userSettingsService.getOrCreateEntity(user.getId());
        FreshnessDeclarations.declare(new FreshnessScope.User(user.getId()), new FreshnessScope.Global());
        return user;
    }

    private User createUserWithUniqueUsername(AuthProviderType provider, String providerId, String email) {
        for (int attempt = 1; attempt <= MAX_USERNAME_RETRIES; attempt++) {
            UsernameComponents username = usernameGenerator.generate();

            User newUser = User.builder()
                    .email(email)
                    .provider(provider)
                    .providerId(providerId)
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
                return switch (ConstraintViolationClassifier.ConstraintName.of(violation)) {
                    case ConstraintViolationClassifier.ConstraintName.Named named ->
                            USERNAME_SUFFIX_CONSTRAINT.equalsIgnoreCase(keyName(named.qualified()));
                    case ConstraintViolationClassifier.ConstraintName.Unnamed unnamed -> false;
                };
            }
        }
        return false;
    }

    /** The key alone: MySQL 8.0.19 and later qualify the key in a duplicate-entry report with its table. */
    private static String keyName(String constraintName) {
        return constraintName.substring(constraintName.lastIndexOf('.') + 1);
    }

    public UserResponse toResponse(User user) {
        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .email(user.getEmail())
                .usernameEpithet(user.getUsernameEpithet())
                .usernameSuffix(user.getUsernameSuffix())
                .role(user.getRole().name());

        switch (Restriction.of(user.getBannedAt(), user.getTimeoutUntil(), clock.instant())) {
            case Restriction.Active active -> { }
            case Restriction.Banned banned -> {
                builder.isBanned(true)
                        .bannedAt(banned.at());
                restrictionReasonProvider.latestBanReason(user.getPublicId())
                        .ifPresent(builder::banReason);
            }
            case Restriction.TimedOut timedOut -> {
                builder.isTimedOut(true)
                        .timeoutUntil(timedOut.until());
                restrictionReasonProvider.latestTimeoutReason(user.getPublicId())
                        .ifPresent(builder::timeoutReason);
            }
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public ScopedRead<UserResponse> describe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserMiss(userId));
        return new ScopedRead<>(toResponse(user), Set.of(new FreshnessScope.User(userId)));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<User> findOptionalById(Long id) {
        return userRepository.findById(id);
    }

    @FreshnessExempt(reason = "startup sentinel check runs with no request bound")
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public ScopedRead<Optional<User>> findActiveById(Long userId) {
        return new ScopedRead<>(userRepository.findByIdAndDeletedAtIsNull(userId),
                Set.of(new FreshnessScope.User(userId)));
    }

    @Transactional
    public Optional<User> findActiveByIdFromPrimary(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public ScopedRead<Optional<User>> findActiveByProvider(AuthProviderType providerType, String providerId) {
        return scopedByFoundUser(
                userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(providerType, providerId));
    }

    @Transactional(readOnly = true)
    public ScopedRead<Optional<User>> findByProvider(AuthProviderType providerType, String providerId) {
        return scopedByFoundUser(userRepository.findByProviderAndProviderId(providerType, providerId));
    }

    private static ScopedRead<Optional<User>> scopedByFoundUser(Optional<User> found) {
        return new ScopedRead<>(found, found
                .<Set<FreshnessScope>>map(user -> Set.of(new FreshnessScope.User(user.getId())))
                .orElse(Set.of()));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<User> findActiveBySuffix(String usernameSuffix) {
        return userRepository.findByUsernameSuffixAndDeletedAtIsNull(usernameSuffix);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public List<User> listActiveAccounts() {
        return userRepository.findByDeletedAtIsNullAndIdNot(UserAccountLifecycleService.SENTINEL_USER_ID);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public List<User> listTimedOutAccounts() {
        return userRepository.findByTimeoutUntilAfterAndDeletedAtIsNull(clock.instant());
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
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

    @Transactional(propagation = Propagation.MANDATORY)
    public User holdActiveById(Long userId) {
        return userRepository.findWithSharedLockByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AccountDeletedException(userId));
    }

    @Transactional
    public UserResponse updateUsernameEpithet(Long userId, String requested) {
        Epithet epithet = epithetValidator.requireValidEpithet(requested);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        WrittenAccount written = WrittenAccount.ofOwnAccount(userId,
                WriteOutcome.of(userRepository.changeEpithet(userId, epithet.getValue(), clock.instant())));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()), new FreshnessScope.Global());

        log.info("User {} updated username epithet to {}", userId, epithet.getValue());

        return toResponse(user.toBuilder().usernameEpithet(epithet.getValue()).build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User changeRole(AllowedRoleChange allowed) {
        User target = allowed.getTarget();
        WrittenAccount written = WrittenAccount.of(target.getId(),
                WriteOutcome.of(userRepository.changeRole(target.getId(), target.getRole(), allowed.getNewRole(), clock.instant())));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()));
        return target.toBuilder().role(allowed.getNewRole()).build();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void timeOut(User target, Instant until) {
        WrittenAccount written = WrittenAccount.of(target.getId(),
                WriteOutcome.of(userRepository.timeOut(
                        target.getId(), target.getRole(), until, clock.instant())));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void clearTimeout(User target) {
        WrittenAccount written = WrittenAccount.of(target.getId(),
                WriteOutcome.of(userRepository.clearTimeout(
                        target.getId(), target.getRole(), clock.instant())));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ban(User target, Long actorId, Instant at) {
        WrittenAccount written = WrittenAccount.of(target.getId(),
                WriteOutcome.of(userRepository.ban(
                        target.getId(), target.getRole(), actorId, at)));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unban(User target) {
        WrittenAccount written = WrittenAccount.of(target.getId(),
                WriteOutcome.of(userRepository.unban(
                        target.getId(), target.getRole(), clock.instant())));
        FreshnessDeclarations.declare(new FreshnessScope.User(written.userId()));
    }
}
