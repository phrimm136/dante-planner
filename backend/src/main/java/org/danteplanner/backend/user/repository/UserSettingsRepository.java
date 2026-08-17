package org.danteplanner.backend.user.repository;

import org.danteplanner.backend.user.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserId(Long userId);

    /**
     * Persists a settings row that does not exist yet.
     *
     * <p>The primary key is the owning account's, shared through {@code @MapsId}, so no id-null
     * guard can tell a new row from an existing one: passing a row that already exists overwrites
     * it.</p>
     *
     * @param settings the settings row to insert
     * @return the persisted settings row
     */
    default UserSettings insert(UserSettings settings) {
        return save(settings);
    }
}
