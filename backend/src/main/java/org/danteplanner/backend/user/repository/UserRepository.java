package org.danteplanner.backend.user.repository;

import jakarta.persistence.LockModeType;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    long countByRoleAndDeletedAtIsNull(UserRole role);

    @Query(value = "SELECT * FROM users WHERE id = :id AND deleted_at IS NULL FOR UPDATE", nativeQuery = true)
    Optional<User> findWithLockByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query(value = "SELECT * FROM users WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<User> findWithLockById(@Param("id") Long id);

    @Query(value = "SELECT * FROM users WHERE id = :id AND deleted_at IS NULL FOR SHARE", nativeQuery = true)
    Optional<User> findWithSharedLockByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NOT NULL "
            + "AND u.permanentDeleteScheduledAt IS NOT NULL "
            + "AND u.permanentDeleteScheduledAt < :cutoff")
    Optional<User> findWithLockPurgeable(@Param("id") Long id, @Param("cutoff") Instant cutoff);

    List<User> findByDeletedAtIsNullAndIdNot(Long id);

    List<User> findByTimeoutUntilAfterAndDeletedAtIsNull(Instant now);

    Optional<User> findByUsernameSuffixAndDeletedAtIsNull(String usernameSuffix);

    @Modifying
    @Query("UPDATE User u SET u.usernameEpithet = :epithet, u.updatedAt = :now "
            + "WHERE u.id = :id AND u.deletedAt IS NULL")
    int changeEpithet(@Param("id") Long id, @Param("epithet") String epithet, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE User u SET u.role = :newRole, u.updatedAt = :now "
            + "WHERE u.id = :id AND u.deletedAt IS NULL AND u.role = :previousRole")
    int changeRole(@Param("id") Long id, @Param("previousRole") UserRole previousRole,
            @Param("newRole") UserRole newRole, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE User u SET u.timeoutUntil = :until, u.updatedAt = :now "
            + "WHERE u.id = :id AND u.deletedAt IS NULL AND u.role = :role")
    int timeOut(@Param("id") Long id, @Param("role") UserRole role,
            @Param("until") Instant until, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE User u SET u.timeoutUntil = NULL, u.updatedAt = :now "
            + "WHERE u.id = :id AND u.deletedAt IS NULL AND u.role = :role")
    int clearTimeout(@Param("id") Long id, @Param("role") UserRole role, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE User u SET u.bannedAt = :at, u.bannedBy = :actorId, u.updatedAt = :at "
            + "WHERE u.id = :id AND u.deletedAt IS NULL AND u.role = :role")
    int ban(@Param("id") Long id, @Param("role") UserRole role,
            @Param("actorId") Long actorId, @Param("at") Instant at);

    @Modifying
    @Query("UPDATE User u SET u.bannedAt = NULL, u.bannedBy = NULL, u.updatedAt = :now "
            + "WHERE u.id = :id AND u.deletedAt IS NULL AND u.role = :role")
    int unban(@Param("id") Long id, @Param("role") UserRole role, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE User u SET u.deletedAt = :at, u.permanentDeleteScheduledAt = :scheduledAt, u.updatedAt = :at "
            + "WHERE u.id = :id AND u.deletedAt IS NULL")
    int softDelete(@Param("id") Long id, @Param("at") Instant at, @Param("scheduledAt") Instant scheduledAt);

    @Modifying
    @Query("UPDATE User u SET u.deletedAt = NULL, u.permanentDeleteScheduledAt = NULL, u.updatedAt = :now "
            + "WHERE u.id = :id AND u.deletedAt IS NOT NULL")
    int reactivate(@Param("id") Long id, @Param("now") Instant now);

    default User insert(User user) {
        Assert.isNull(user.getId(), "insert() takes new rows only");
        return save(user);
    }
}
