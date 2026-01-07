package org.danteplanner.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for administrative operations.
 * Only ADMIN users can perform these operations (enforced at controller level).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Change a user's role with safeguards.
     *
     * @param actorId   the admin performing the action
     * @param targetId  the user whose role is being changed
     * @param newRole   the new role to assign
     * @return the updated user
     * @throws UserNotFoundException if target user not found
     * @throws IllegalArgumentException if safeguard violated
     */
    @Transactional
    public User changeRole(Long actorId, Long targetId, UserRole newRole) {
        // Use pessimistic locking to prevent TOCTOU race conditions
        User actor = userRepository.findWithLockByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userRepository.findWithLockByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        UserRole actorRole = actor.getRole();
        UserRole targetCurrentRole = target.getRole();

        // Safeguard 1: Cannot grant role higher than own
        if (newRole.outranks(actorRole)) {
            throw new IllegalArgumentException("Cannot grant role higher than your own");
        }

        // Safeguard 2: Cannot modify user of equal or higher rank (unless self-demotion)
        if (!actorId.equals(targetId) && targetCurrentRole.hasRankAtLeast(actorRole)) {
            throw new IllegalArgumentException("Cannot modify user of equal or higher rank");
        }

        // Safeguard 3: Cannot demote last admin
        if (targetCurrentRole == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot demote the last administrator");
            }
        }

        // Apply role change
        UserRole oldRole = target.getRole();
        target.setRole(newRole);
        User saved = userRepository.save(target);

        // If demoted, invalidate all their tokens immediately
        if (oldRole.outranks(newRole)) {
            tokenBlacklistService.invalidateUserTokens(targetId);
            log.info("User {} demoted from {} to {} by admin {}. Tokens invalidated.",
                    targetId, oldRole, newRole, actorId);
        } else {
            log.info("User {} role changed from {} to {} by admin {}",
                    targetId, oldRole, newRole, actorId);
        }

        return saved;
    }

    /**
     * Get a user's current role.
     *
     * @param userId the user ID
     * @return the user's role
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserRole getUserRole(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return user.getRole();
    }
}
