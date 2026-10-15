package org.danteplanner.backend.user.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.user.api.AccountLifecycleParticipant;
import org.danteplanner.backend.user.api.SessionEndHandler;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.validation.RoleChangeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for user account lifecycle operations.
 */
@Service
@Slf4j
public class UserAccountLifecycleService {

    public static final Long SENTINEL_USER_ID = Users.SENTINEL_USER_ID;

    private final UserRepository userRepository;
    private final List<AccountLifecycleParticipant> participants;
    private final SessionEndHandler sessionEndHandler;
    private final RoleChangeValidator roleChangeValidator;
    private final int gracePeriodDays;

    public UserAccountLifecycleService(
            UserRepository userRepository,
            List<AccountLifecycleParticipant> participants,
            SessionEndHandler sessionEndHandler,
            RoleChangeValidator roleChangeValidator,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.userRepository = userRepository;
        this.participants = participants;
        this.sessionEndHandler = sessionEndHandler;
        this.roleChangeValidator = roleChangeValidator;
        this.gracePeriodDays = gracePeriodDays;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Instant deleteAccount(Long userId) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isDeleted()) {
            return user.getPermanentDeleteScheduledAt();
        }

        if (user.getRole() == UserRole.ADMIN) {
            roleChangeValidator.requireAnotherActiveAdministrator();
        }

        Instant scheduledDeleteAt = Instant.now().plus(Duration.ofDays(gracePeriodDays));
        user.softDelete(scheduledDeleteAt);
        FreshnessDeclarations.declare(new FreshnessScope.Author(userId));

        participants.forEach(participant -> participant.accountDeactivated(userId));
        sessionEndHandler.endAllSessions(userId);
        log.info("User {} requested account deletion", userId);

        return scheduledDeleteAt;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reactivateAccount(Long userId) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            return;
        }

        user.reactivate();
        FreshnessDeclarations.declare(new FreshnessScope.Author(userId));
        participants.forEach(participant -> participant.accountReactivated(userId));
    }

    @Transactional
    public boolean performHardDelete(Long userId, Instant cutoff) {
        Optional<User> purgeable = userRepository.findWithLockPurgeable(userId, cutoff);
        if (purgeable.isEmpty()) {
            return false;
        }
        User user = purgeable.get();

        try {
            sessionEndHandler.endAllSessions(userId);
        } catch (DataAccessException e) {
            log.warn("Token invalidation unavailable for purged user {}; tokens lapse at expiry",
                    userId, e);
        }

        participants.forEach(participant -> participant.reassignToSentinel(userId, SENTINEL_USER_ID));
        participants.forEach(participant -> participant.deleteOwnedData(userId));

        // The user row's CASCADE removes the planner cores and their FK-bearing children.
        userRepository.delete(user);
        return true;
    }
}
