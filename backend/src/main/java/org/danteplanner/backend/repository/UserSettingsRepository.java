package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserId(Long userId);

    /**
     * Find user IDs who have notifyNewPublications enabled.
     * Excludes a specific user (typically the author).
     * Only includes users who are not deleted.
     *
     * @param excludeUserId the user ID to exclude
     * @return list of user IDs with the setting enabled
     */
    @Query("SELECT s.userId FROM UserSettings s " +
           "JOIN User u ON s.userId = u.id " +
           "WHERE s.notifyNewPublications = true " +
           "AND u.deletedAt IS NULL " +
           "AND s.userId <> :excludeUserId")
    List<Long> findUserIdsWithNewPublicationsEnabled(@Param("excludeUserId") Long excludeUserId);
}
