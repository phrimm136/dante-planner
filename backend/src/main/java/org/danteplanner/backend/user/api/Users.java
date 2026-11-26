package org.danteplanner.backend.user.api;

import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.user.api.dto.UserResponse;

import java.util.Collection;
import java.util.List;

public interface Users {

    Long SENTINEL_USER_ID = 0L;

    Option<UserSummary> findActiveById(Long userId);

    UserSummary findOrCreateUser(String provider, String providerId, String email);

    Option<UserSummary> findByProvider(AuthProviderType providerType, String providerId);

    Option<UserSummary> findActiveByProvider(AuthProviderType providerType, String providerId);

    UserSummary findById(Long id);

    UserResponse describeById(Long id);

    List<UserSummary> findAllByIds(Collection<Long> ids);

    List<UserSummary> listActiveAccounts();

    List<UserSummary> listTimedOutAccounts();

    UserSummary holdActiveById(Long userId);

    Option<UserSummary> findOptionalById(Long id);

    void reactivateAccount(Long userId);

    RoleChange changeRole(Long actorId, Long targetId, UserRole newRole);
}
