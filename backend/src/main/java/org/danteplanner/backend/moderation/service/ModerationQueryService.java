package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.dto.ModeratedUserResponse;
import org.danteplanner.backend.moderation.dto.ModerationActionResponse;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read side of the moderation dashboard: the user roster, who is currently restricted, and the
 * audit trail with its actors resolved.
 */
@Service
@RequiredArgsConstructor
public class ModerationQueryService {

    private static final int RECENT_ACTION_LIMIT = 100;
    private static final Set<FreshnessScope> GLOBAL = Set.of(new FreshnessScope.Global());

    private final Users users;
    private final ModerationActionRepository moderationActionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScopedRead<List<ModeratedUserResponse>> getAllUsers() {
        Instant now = clock.instant();
        return new ScopedRead<>(users.listActiveAccounts().stream()
                .map(user -> ModeratedUserResponse.fromUser(user, now))
                .toList(), GLOBAL);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public List<ModerationAction> getModerationActions() {
        return moderationActionRepository.findRecent(PageRequest.ofSize(RECENT_ACTION_LIMIT));
    }

    @Transactional(readOnly = true)
    public ScopedRead<List<ModerationActionResponse>> getModerationActionsWithActors() {
        List<ModerationAction> actions = getModerationActions();

        List<Long> actorIds = actions.stream()
                .map(ModerationAction::getActorId)
                .distinct()
                .toList();

        Map<Long, UserSummary> actorMap = users.findAllByIds(actorIds).stream()
                .collect(Collectors.toMap(UserSummary::id, user -> user));

        return new ScopedRead<>(actions.stream()
                .map(action -> {
                    UserSummary actor = actorMap.get(action.getActorId());
                    String epithet = actor != null ? actor.usernameEpithet() : "Unknown";
                    String suffix = actor != null ? actor.usernameSuffix() : "";
                    return ModerationActionResponse.fromEntity(action, epithet, suffix);
                })
                .toList(), GLOBAL);
    }

    @Transactional(readOnly = true)
    public ScopedRead<List<UserSummary>> getTimedOutUsers() {
        return new ScopedRead<>(users.listTimedOutAccounts(), GLOBAL);
    }
}
