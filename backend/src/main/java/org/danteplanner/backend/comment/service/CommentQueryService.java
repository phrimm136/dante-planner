package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads comments: the threaded view a planner page renders, and the lookups other services resolve
 * an id through.
 */
@Service
@RequiredArgsConstructor
public class CommentQueryService {

    private final PlannerCommentRepository commentRepository;
    private final PlannerCommentVoteRepository commentVoteRepository;
    private final UserService userService;
    private final PlannerAccessGuard accessGuard;
    private final CommentAccessValidator accessValidator;

    @Transactional(readOnly = true)
    public List<CommentTreeNode> getCommentTree(UUID plannerId, Long currentUserId) {
        Planner planner = accessGuard.requireExisting(plannerId);

        accessValidator.requireThreadVisible(planner, currentUserId);

        List<PlannerComment> comments = commentRepository.findByPlannerId(plannerId);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = comments.stream()
                .map(PlannerComment::getUserId)
                .filter(id -> !UserAccountLifecycleService.SENTINEL_USER_ID.equals(id))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> upvotedIds = Collections.emptySet();
        if (currentUserId != null) {
            List<Long> commentIds = comments.stream()
                    .map(PlannerComment::getId)
                    .toList();
            upvotedIds = new HashSet<>(commentVoteRepository.findUpvotedCommentIds(commentIds, currentUserId));
        }

        return buildCommentTree(comments, userMap, upvotedIds, currentUserId);
    }

    @Transactional(readOnly = true)
    public CommentTreeNode broadcastNode(PlannerComment comment, UUID parentPublicId) {
        User author = userService.findOptionalById(comment.getUserId()).orElse(null);
        return CommentTreeNode.forBroadcast(comment, parentPublicId, author);
    }

    @Transactional(readOnly = true)
    public PlannerComment requireById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    @Transactional(readOnly = true)
    public PlannerComment requireByPublicId(UUID commentPublicId) {
        return commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));
    }

    private List<CommentTreeNode> buildCommentTree(
            List<PlannerComment> comments,
            Map<Long, User> userMap,
            Set<Long> upvotedIds,
            Long currentUserId
    ) {
        Map<Long, List<PlannerComment>> childrenMap = comments.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(PlannerComment::getParentCommentId));

        List<PlannerComment> topLevel = comments.stream()
                .filter(c -> c.getParentCommentId() == null)
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .toList();

        return topLevel.stream()
                .map(c -> buildNode(c, null, childrenMap, userMap, upvotedIds, currentUserId))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CommentTreeNode> buildNode(
            PlannerComment comment,
            UUID parentPublicId,
            Map<Long, List<PlannerComment>> childrenMap,
            Map<Long, User> userMap,
            Set<Long> upvotedIds,
            Long currentUserId
    ) {
        List<PlannerComment> children = childrenMap.getOrDefault(comment.getId(), Collections.emptyList());
        List<CommentTreeNode> childNodes = children.stream()
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .map(c -> buildNode(c, comment.getPublicId(), childrenMap, userMap, upvotedIds, currentUserId))
                .flatMap(Optional::stream)
                .toList();

        if (comment.isDeleted() && childNodes.isEmpty()) {
            return Optional.empty();
        }

        User author = userMap.get(comment.getUserId());
        boolean hasUpvoted = upvotedIds.contains(comment.getId());

        return Optional.of(CommentTreeNode.fromEntity(
                comment, parentPublicId, author, currentUserId, hasUpvoted, childNodes));
    }
}
