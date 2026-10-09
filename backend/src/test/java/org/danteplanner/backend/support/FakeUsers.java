package org.danteplanner.backend.support;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.test.util.ReflectionTestUtils;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.LastAdministratorException;
import org.danteplanner.backend.user.api.RestrictionReasonProvider;
import org.danteplanner.backend.user.api.RoleChange;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.user.exception.UserForbiddenException;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.user.service.UsersApiAdapter;

public class FakeUsers implements Users {

    private static final String CREATED_EPITHET = "W_CORP";

    private static final RestrictionReasonProvider NO_RECORDED_REASONS = new RestrictionReasonProvider() {
        @Override
        public Optional<String> latestBanReason(UUID targetPublicId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> latestTimeoutReason(UUID targetPublicId) {
            return Optional.empty();
        }
    };

    private final Map<Long, User> rows = new LinkedHashMap<>();
    private final UserService responses = new UserService(null, null, null, NO_RECORDED_REASONS, null, null);
    private long nextId = 1;

    public User add(User user) {
        requireUnique(user);
        if (user.getId() == null) {
            ReflectionTestUtils.setField(user, "id", nextFreeId());
        }
        if (user.getPublicId() == null) {
            ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
        }
        rows.put(user.getId(), user);
        return user;
    }

    private void requireUnique(User user) {
        if (user.getId() != null && rows.containsKey(user.getId())) {
            throw new IllegalStateException("Duplicate users.PRIMARY id " + user.getId());
        }
        for (User row : rows.values()) {
            if (user.getPublicId() != null && user.getPublicId().equals(row.getPublicId())) {
                throw new IllegalStateException("Duplicate users.idx_user_public_id " + user.getPublicId());
            }
            if (user.getProvider() == row.getProvider() && user.getProviderId() != null
                    && user.getProviderId().equalsIgnoreCase(row.getProviderId())) {
                throw new IllegalStateException("Duplicate users.uk_provider_provider_id "
                        + user.getProvider() + "/" + user.getProviderId());
            }
            if (user.getUsernameSuffix() != null && user.getUsernameSuffix().equalsIgnoreCase(row.getUsernameSuffix())) {
                throw new IllegalStateException("Duplicate users.uk_users_username_suffix " + user.getUsernameSuffix());
            }
        }
    }

    @Override
    public Optional<UserSummary> findActiveById(Long userId) {
        return active(userId).map(UsersApiAdapter::toSummary);
    }

    @Override
    public UserSummary findOrCreateUser(String provider, Map<String, String> userInfo) {
        AuthProviderType providerType = AuthProviderType.fromValue(provider);
        String providerId = userInfo.get("id");
        User user = first(byProvider(providerType, providerId)).orElseGet(() -> add(User.builder()
                .email(userInfo.get("email"))
                .provider(providerType)
                .providerId(providerId)
                .usernameEpithet(CREATED_EPITHET)
                .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                .build()));
        return UsersApiAdapter.toSummary(user);
    }

    @Override
    public Optional<UserSummary> findByProvider(AuthProviderType providerType, String providerId) {
        return first(byProvider(providerType, providerId)).map(UsersApiAdapter::toSummary);
    }

    @Override
    public Optional<UserSummary> findActiveByProvider(AuthProviderType providerType, String providerId) {
        return first(byProvider(providerType, providerId).and(user -> !user.isDeleted()))
                .map(UsersApiAdapter::toSummary);
    }

    @Override
    public UserSummary findById(Long id) {
        return UsersApiAdapter.toSummary(row(id));
    }

    @Override
    public UserResponse describeById(Long id) {
        return responses.toResponse(row(id));
    }

    @Override
    public Optional<UserSummary> findActiveBySuffix(String usernameSuffix) {
        return first(user -> !user.isDeleted() && usernameSuffix.equalsIgnoreCase(user.getUsernameSuffix()))
                .map(UsersApiAdapter::toSummary);
    }

    @Override
    public List<UserSummary> findAllByIds(Collection<Long> ids) {
        return ids.stream().distinct().map(rows::get).filter(Objects::nonNull)
                .map(UsersApiAdapter::toSummary).toList();
    }

    @Override
    public List<UserSummary> listActiveAccounts() {
        return rows.values().stream()
                .filter(user -> !user.isDeleted() && !SENTINEL_USER_ID.equals(user.getId()))
                .map(UsersApiAdapter::toSummary).toList();
    }

    @Override
    public List<UserSummary> listTimedOutAccounts() {
        Instant now = Instant.now();
        return rows.values().stream()
                .filter(user -> !user.isDeleted() && user.getTimeoutUntil() != null
                        && user.getTimeoutUntil().isAfter(now))
                .map(UsersApiAdapter::toSummary).toList();
    }

    @Override
    public UserSummary holdActiveById(Long userId) {
        return UsersApiAdapter.toSummary(active(userId).orElseThrow(() -> new AccountDeletedException(userId)));
    }

    @Override
    public Optional<UserSummary> findOptionalById(Long id) {
        return Optional.ofNullable(rows.get(id)).map(UsersApiAdapter::toSummary);
    }

    @Override
    public void reactivateAccount(Long userId) {
        User user = row(userId);
        if (user.isDeleted()) {
            user.reactivate();
        }
    }

    @Override
    public RoleChange changeRole(Long actorId, Long targetId, UserRole newRole) {
        User actor = active(actorId).orElseThrow(() -> new UserNotFoundException(actorId));
        User target = active(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        UserRole previousRole = target.getRole();

        if (newRole.outranks(actor.getRole())) {
            throw new UserForbiddenException("Cannot grant role higher than your own");
        }
        if (!actor.getId().equals(target.getId()) && target.getRole().hasRankAtLeast(actor.getRole())) {
            throw new UserForbiddenException("Cannot modify user of equal or higher rank");
        }
        if (previousRole == UserRole.ADMIN && newRole != UserRole.ADMIN
                && rows.values().stream().filter(user -> !user.isDeleted() && user.getRole() == UserRole.ADMIN)
                        .count() <= 1) {
            throw new LastAdministratorException();
        }

        target.setRole(newRole);
        return new RoleChange(previousRole, UsersApiAdapter.toSummary(target));
    }

    private long nextFreeId() {
        while (rows.containsKey(nextId)) {
            nextId++;
        }
        return nextId;
    }

    private User row(Long id) {
        User user = rows.get(id);
        if (user == null) {
            throw new UserNotFoundException(id);
        }
        return user;
    }

    private Optional<User> active(Long id) {
        return Optional.ofNullable(rows.get(id)).filter(user -> !user.isDeleted());
    }

    private Optional<User> first(Predicate<User> matching) {
        return rows.values().stream().filter(matching).findFirst();
    }

    private static Predicate<User> byProvider(AuthProviderType providerType, String providerId) {
        return user -> user.getProvider() == providerType && providerId != null
                && providerId.equalsIgnoreCase(user.getProviderId());
    }
}
