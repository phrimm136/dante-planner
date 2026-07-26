package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.dto.ModerationActionDto;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read side of the moderation dashboard: the user roster, who is currently restricted, and the
 * audit trail with its actors resolved.
 */
@Service
@RequiredArgsConstructor
public class ModerationQueryService {

    /** Newest audit records returned to the dashboard. */
    private static final int RECENT_ACTION_LIMIT = 100;

    private final UserService userService;
    private final ModerationActionRepository moderationActionRepository;

    /**
     * Get all users for moderation dashboard.
     * Excludes soft-deleted users and the sentinel account that owns anonymized content.
     *
     * @return list of all active users
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userService.listActiveAccounts();
    }

    /**
     * Get all moderation actions for audit trail.
     * Returns the most recent actions, newest first.
     *
     * @return list of moderation actions
     */
    @Transactional(readOnly = true)
    public List<ModerationAction> getModerationActions() {
        return moderationActionRepository.findRecent(PageRequest.ofSize(RECENT_ACTION_LIMIT));
    }

    /**
     * Get moderation actions with actor information for API response.
     * Batch-fetches all actors to avoid N+1 queries, and exposes actor usernames rather than
     * internal IDs.
     *
     * @return list of moderation action DTOs with actor information
     */
    @Transactional(readOnly = true)
    public List<ModerationActionDto> getModerationActionsWithActors() {
        List<ModerationAction> actions = getModerationActions();

        List<Long> actorIds = actions.stream()
                .map(ModerationAction::getActorId)
                .distinct()
                .toList();

        Map<Long, User> actorMap = userService.findAllByIds(actorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return actions.stream()
                .map(action -> {
                    User actor = actorMap.get(action.getActorId());
                    String epithet = actor != null ? actor.getUsernameEpithet() : "Unknown";
                    String suffix = actor != null ? actor.getUsernameSuffix() : "";
                    return ModerationActionDto.fromEntity(action, epithet, suffix);
                })
                .toList();
    }

    /**
     * Get all currently timed-out users.
     * Uses the V014 partial index on timeout_until for efficient lookup.
     *
     * @return list of users with active timeouts
     */
    @Transactional(readOnly = true)
    public List<User> getTimedOutUsers() {
        return userService.listTimedOutAccounts();
    }
}
