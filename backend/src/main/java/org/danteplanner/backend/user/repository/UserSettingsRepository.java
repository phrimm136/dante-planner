package org.danteplanner.backend.user.repository;

import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserId(Long userId);

    @Query(value = "SELECT * FROM user_settings WHERE user_id = :userId FOR UPDATE", nativeQuery = true)
    Optional<UserSettings> findWithLockByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = "INSERT INTO user_settings (user_id) SELECT u.id FROM users u WHERE u.id = :userId "
            + "ON DUPLICATE KEY UPDATE user_id = user_settings.user_id", nativeQuery = true)
    int insertDefaults(@Param("userId") Long userId);

    /**
     * <p>Sync can only be on once it has been chosen, an invariant the
     * {@code ck_user_settings_sync_choice} check constraint also enforces.</p>
     */
    @Modifying
    @Query("""
            UPDATE UserSettings s SET
                s.syncEnabled = COALESCE(:#{#request.syncEnabled()}, s.syncEnabled),
                s.syncChoiceMade = COALESCE(:#{#request.syncEnabled() != null ? true : null}, s.syncChoiceMade),
                s.notifyComments = COALESCE(:#{#request.notifyComments()}, s.notifyComments),
                s.notifyRecommendations = COALESCE(:#{#request.notifyRecommendations()}, s.notifyRecommendations),
                s.notifyNewPublications = COALESCE(:#{#request.notifyNewPublications()}, s.notifyNewPublications)
            WHERE s.userId = :userId
            """)
    int update(@Param("userId") Long userId, @Param("request") UpdateUserSettingsRequest request);
}
