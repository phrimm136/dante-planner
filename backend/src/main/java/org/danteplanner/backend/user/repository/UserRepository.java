package org.danteplanner.backend.user.repository;

import jakarta.persistence.LockModeType;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(AuthProviderType provider, String providerId);

    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(AuthProviderType provider, String providerId);

    List<User> findByPermanentDeleteScheduledAtBefore(Instant cutoff);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    long countByRole(UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NOT NULL "
            + "AND u.permanentDeleteScheduledAt IS NOT NULL "
            + "AND u.permanentDeleteScheduledAt < :cutoff")
    Optional<User> findWithLockPurgeable(@Param("id") Long id, @Param("cutoff") Instant cutoff);

    List<User> findByDeletedAtIsNullAndIdNot(Long id);

    List<User> findByTimeoutUntilAfterAndDeletedAtIsNull(Instant now);

    Optional<User> findByUsernameSuffixAndDeletedAtIsNull(String usernameSuffix);

    default User insert(User user) {
        Assert.isNull(user.getId(), "insert() takes new rows only");
        return save(user);
    }
}
