package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.moderation.dto.ModerationActionResponse;
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

    private static final int RECENT_ACTION_LIMIT = 100;

    private final UserService userService;
    private final ModerationActionRepository moderationActionRepository;

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userService.listActiveAccounts();
    }

    @Transactional(readOnly = true)
    public List<ModerationAction> getModerationActions() {
        return moderationActionRepository.findRecent(PageRequest.ofSize(RECENT_ACTION_LIMIT));
    }

    @Transactional(readOnly = true)
    public List<ModerationActionResponse> getModerationActionsWithActors() {
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
                    return ModerationActionResponse.fromEntity(action, epithet, suffix);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<User> getTimedOutUsers() {
        return userService.listTimedOutAccounts();
    }
}
