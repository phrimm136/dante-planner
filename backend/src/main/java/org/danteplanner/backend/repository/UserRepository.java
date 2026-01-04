package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /**
     * Find an active (non-deleted) user by OAuth provider credentials.
     * Used for authentication to exclude soft-deleted users.
     *
     * @param provider   the OAuth provider (e.g., "google", "apple")
     * @param providerId the provider's user ID
     * @return the active user if found
     */
    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(String provider, String providerId);

    /**
     * Find users scheduled for permanent deletion before the cutoff time.
     * Used by the cleanup scheduler to find expired users.
     *
     * @param cutoff the cutoff instant (users scheduled before this are eligible)
     * @return list of users ready for hard deletion
     */
    List<User> findByPermanentDeleteScheduledAtBefore(Instant cutoff);

    /**
     * Find an active (non-deleted) user by ID.
     * Used for operations that should only work on non-deleted users.
     *
     * @param id the user ID
     * @return the active user if found
     */
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
}
