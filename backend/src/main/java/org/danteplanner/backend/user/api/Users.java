package org.danteplanner.backend.user.api;

import org.danteplanner.backend.user.api.dto.UserResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Users {

    Long SENTINEL_USER_ID = 0L;

    Optional<UserSummary> findActiveById(Long userId);

    UserSummary findOrCreateUser(String provider, Map<String, String> userInfo);

    Optional<UserSummary> findByProvider(AuthProviderType providerType, String providerId);

    Optional<UserSummary> findActiveByProvider(AuthProviderType providerType, String providerId);

    UserSummary findById(Long id);

    UserResponse describeById(Long id);

    Optional<UserSummary> findActiveBySuffix(String usernameSuffix);

    List<UserSummary> findAllByIds(Collection<Long> ids);

    List<UserSummary> listActiveAccounts();

    List<UserSummary> listTimedOutAccounts();

    UserSummary holdActiveById(Long userId);

    Optional<UserSummary> findOptionalById(Long id);

    void reactivateAccount(Long userId);

    RoleChange changeRole(Long actorId, Long targetId, UserRole newRole);
}
