package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.RoleChange;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.validation.RoleChangeValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsersApiAdapter implements Users {

    private final UserService userService;
    private final UserAccountLifecycleService lifecycleService;
    private final RoleChangeValidator roleChangeValidator;

    public static UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getPublicId(),
                user.getEmail(),
                user.getUsernameEpithet(),
                user.getUsernameSuffix(),
                user.getRole(),
                user.isDeleted(),
                user.getBannedAt(),
                user.getTimeoutUntil());
    }

    @Override
    public Optional<UserSummary> findActiveById(Long userId) {
        return userService.findActiveById(userId).map(UsersApiAdapter::toSummary);
    }

    @Override
    public UserSummary findOrCreateUser(String provider, Map<String, String> userInfo) {
        return toSummary(userService.findOrCreateUser(provider, userInfo));
    }

    @Override
    public Optional<UserSummary> findByProvider(AuthProviderType providerType, String providerId) {
        return userService.findByProvider(providerType, providerId).map(UsersApiAdapter::toSummary);
    }

    @Override
    public Optional<UserSummary> findActiveByProvider(AuthProviderType providerType, String providerId) {
        return userService.findActiveByProvider(providerType, providerId).map(UsersApiAdapter::toSummary);
    }

    @Override
    public UserSummary findById(Long id) {
        return toSummary(userService.findById(id));
    }

    @Override
    public UserResponse describeById(Long id) {
        return userService.toResponse(userService.findById(id));
    }

    @Override
    public Optional<UserSummary> findActiveBySuffix(String usernameSuffix) {
        return userService.findActiveBySuffix(usernameSuffix).map(UsersApiAdapter::toSummary);
    }

    @Override
    public List<UserSummary> findAllByIds(Collection<Long> ids) {
        return userService.findAllByIds(ids).stream().map(UsersApiAdapter::toSummary).toList();
    }

    @Override
    public List<UserSummary> listActiveAccounts() {
        return userService.listActiveAccounts().stream().map(UsersApiAdapter::toSummary).toList();
    }

    @Override
    public List<UserSummary> listTimedOutAccounts() {
        return userService.listTimedOutAccounts().stream().map(UsersApiAdapter::toSummary).toList();
    }

    @Override
    public UserSummary holdActiveById(Long userId) {
        return toSummary(userService.holdActiveById(userId));
    }

    @Override
    public Optional<UserSummary> findOptionalById(Long id) {
        return userService.findOptionalById(id).map(UsersApiAdapter::toSummary);
    }

    @Override
    public void reactivateAccount(Long userId) {
        lifecycleService.reactivateAccount(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RoleChange changeRole(Long actorId, Long targetId, UserRole newRole) {
        User actor = userService.lockActiveById(actorId);
        User target = userService.lockActiveById(targetId);
        UserRole previousRole = target.getRole();

        roleChangeValidator.requireCanChangeRole(actor, target, newRole);

        target.setRole(newRole);
        return new RoleChange(previousRole, toSummary(target));
    }
}
